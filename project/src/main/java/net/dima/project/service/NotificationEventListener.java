package net.dima.project.service;

import lombok.RequiredArgsConstructor;
import net.dima.project.entity.ContainerCargoEntity;
import net.dima.project.entity.ContainerEntity;
import net.dima.project.entity.NotificationEvents.*;
import net.dima.project.entity.OfferEntity;
import net.dima.project.entity.OfferStatus;
import net.dima.project.entity.RequestEntity;
import net.dima.project.entity.UserEntity;
import net.dima.project.dto.RequestCardDto;
import net.dima.project.repository.ContainerCargoRepository;
import net.dima.project.repository.UserRepository;
import net.dima.project.repository.OfferRepository; // <-- ✅ import 추가

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.dima.project.dto.ShipmentStatusUpdateDto;
import net.dima.project.repository.RequestRepository;
import net.dima.project.dto.BidCountUpdateDto;
import net.dima.project.dto.DashboardMetricsDto;
import net.dima.project.dto.OfferStatusUpdateDto; // ✅ DTO import 추가
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;
    private final ChatService chatService; // ChatService를 주입받습니다.
    private final ContainerCargoRepository containerCargoRepository;
    private final SseEmitterService sseEmitterService;
    private final UserRepository userRepository;
    private final OfferRepository offerRepository;
    private final RequestRepository requestRepository;
    private final AdminService adminService;
    
    /**
     * 신규 제안 생성 이벤트를 처리합니다.
     */
    @Async
    @EventListener
    public void handleOfferCreatedEvent(OfferCreatedEvent event) {
        OfferEntity offer = event.getOffer();
        RequestEntity request = offer.getRequest();
        UserEntity requester = request.getRequester();

        // 1. 기존의 텍스트 알림 전송 (그대로 유지)
        String itemName = request.getCargo().getItemName();
        String message = String.format("'%s' 요청에 새로운 제안이 도착했습니다.", itemName);
        String url = (requester.getRoles().contains("cus")) ? "/cus/cusRequest" : "/fwd/my-posted-requests";
        notificationService.sendNotification(requester, message, url);

        // --- [✅ 여기부터 추가된 로직] ---
        // 2. 실시간 UI 업데이트를 위한 SSE 이벤트를 전송합니다.
        long newBidderCount = offerRepository.countByRequest(request); // 현재 요청의 총 제안 수를 다시 계산
        BidCountUpdateDto updateDto = BidCountUpdateDto.builder()
                .requestId(request.getRequestId())
                .bidderCount(newBidderCount)
                .build();
        
        sseEmitterService.sendToClient(requester.getUserId(), "bid_count_update", updateDto);
        // --- [✅ 여기까지 추가된 로직] ---
    }

    /**
     * 제안 확정(낙찰/거절) 이벤트를 처리합니다.
     */
    @EventListener
    public void handleOfferConfirmedEvent(OfferConfirmedEvent event) {
        OfferEntity winningOffer = event.getWinningOffer();
        String itemName = winningOffer.getRequest().getCargo().getItemName();
        String url = "/fwd/my-offers";

        // 관련된 모든 제안을 순회하며 알림 및 SSE 이벤트를 발송
        for (OfferEntity offer : event.getOffers()) {
            UserEntity forwarder = offer.getForwarder();
            OfferStatusUpdateDto updateDto;

            if (offer.equals(winningOffer)) {
                // --- 낙찰된 포워더에게 ---
                // 1. 텍스트 알림 전송
                String message = String.format("축하합니다! '%s' 제안이 낙찰되었습니다.", itemName);
                notificationService.sendNotification(forwarder, message, url);

                // 2. 실시간 UI 업데이트용 SSE 이벤트 전송
                updateDto = OfferStatusUpdateDto.builder()
                        .offerId(offer.getOfferId())
                        .status(OfferStatus.ACCEPTED.name())
                        .statusText("수락")
                        .build();

            } else {
                // --- 유찰된 포워더에게 ---
                // 1. 텍스트 알림 전송
                String message = String.format("아쉽지만 '%s' 제안은 마감되었습니다.", itemName);
                notificationService.sendNotification(forwarder, message, url);

                // 2. 실시간 UI 업데이트용 SSE 이벤트 전송
                updateDto = OfferStatusUpdateDto.builder()
                        .offerId(offer.getOfferId())
                        .status(OfferStatus.REJECTED.name())
                        .statusText("거절")
                        .build();
            }
            
            sseEmitterService.sendToClient(forwarder.getUserId(), "offer_status_update", updateDto);
        }
        
        // 채팅방 생성 로직은 그대로 유지
        chatService.createChatRoomForOffer(winningOffer);
        triggerDashboardUpdate();
    }

    /**
     * 컨테이너 상태 변경 이벤트를 처리합니다. (수정된 최종 버전)
     */
    @EventListener
    public void handleContainerStatusChangedEvent(ContainerStatusChangedEvent event) {
        ContainerEntity container = event.getContainer();
        String message = String.format("컨테이너 '%s'의 상태가 변경되었습니다: %s",
                container.getContainerId(), event.getMessage());
        String cusUrl = "/cus/tracking";
        String fwdUrl = "/fwd/my-posted-requests"; // 재판매 요청 관리 페이지

        Set<UserEntity> receivers = new HashSet<>();

        // 1. 컨테이너에 연결된 모든 '제안(Offer)'을 직접 조회합니다. 이 방법이 훨씬 안정적입니다.
        List<OfferEntity> offersInContainer = offerRepository.findAllByContainer(container);

        for (OfferEntity offer : offersInContainer) {
            // 2. 각 제안에 연결된 요청(Request)을 가져옵니다.
            RequestEntity initialRequest = offer.getRequest();
            if (initialRequest == null) continue;

            // 3. 재판매 체인을 따라 올라가며 모든 관련자(화주, 중간 포워더)를 receivers에 추가합니다.
            RequestEntity currentRequest = initialRequest;
            while (currentRequest != null) {
                UserEntity requester = currentRequest.getRequester();
                receivers.add(requester); // 요청자를 수신자에 추가

                // 4. 실시간 UI 업데이트를 위한 SSE 이벤트를 각 요청자에게 전송합니다.
                ShipmentStatusUpdateDto updateDto = ShipmentStatusUpdateDto.builder()
                        .requestId(currentRequest.getRequestId())
                        .detailedStatus(container.getStatus().name())
                        .build();
                sseEmitterService.sendToClient(requester.getUserId(), "shipment_update", updateDto);

                // 재판매 체인의 상위 요청으로 이동
                currentRequest = (currentRequest.getSourceOffer() != null) ?
                                 currentRequest.getSourceOffer().getRequest() :
                                 null;
            }
            
            // 5. 최초 화주(Owner)도 빠짐없이 수신자에 추가합니다.
            receivers.add(initialRequest.getCargo().getOwner());
        }

        // 6. 수집된 모든 관련자에게 역할에 맞는 URL로 텍스트 알림을 보냅니다.
        for (UserEntity receiver : receivers) {
            // 포워더가 자신의 액션에 대한 알림은 받지 않도록 필터링합니다.
            if (!receiver.getUserSeq().equals(container.getForwarder().getUserSeq())) {
                String finalUrl = receiver.getRoles().contains("cus") ? cusUrl : fwdUrl;
                notificationService.sendNotification(receiver, message, finalUrl);
            }
        }
    }
        
    
    /**
     * 신규 화물 요청 생성 이벤트를 처리합니다. (수정된 버전)
     */
    @Async
    @EventListener
    public void handleRequestCreatedEvent(RequestCreatedEvent event) {
        RequestCardDto newRequestDto = event.getRequestCardDto();

        // 1. 'ROLE_fwd' 역할을 가진 모든 사용자를 DB에서 조회합니다.
        List<UserEntity> forwarders = userRepository.findByRolesIn(List.of("ROLE_fwd"));

        // 2. 현재 SSE에 연결된 사용자 목록을 가져옵니다.
        Set<String> connectedUserIds = sseEmitterService.getEmitters().keySet();

        // 3. 포워더 중에서 현재 연결된 사용자에게만 이벤트를 전송합니다.
        forwarders.stream()
            .map(UserEntity::getUserId) // UserEntity에서 userId만 추출
            .filter(connectedUserIds::contains) // 연결된 사용자인지 확인
            .forEach(userId -> sseEmitterService.sendToClient(userId, "new_request", newRequestDto));
        
        // ✅ 관리자 대시보드 업데이트 트리거를 호출합니다.
        triggerDashboardUpdate();
    }
    
    private void triggerDashboardUpdate() {
        // 1. 최신 대시보드 데이터를 AdminService를 통해 가져옵니다.
        DashboardMetricsDto latestMetrics = adminService.getDashboardMetrics();

        // 2. 'ROLE_admin' 역할을 가진 모든 관리자를 찾습니다.
        List<UserEntity> admins = userRepository.findByRolesIn(List.of("ROLE_admin"));
        
        // 3. 현재 SSE에 연결된 사용자인지 확인하고, 관리자에게만 이벤트를 보냅니다.
        Set<String> connectedUserIds = sseEmitterService.getEmitters().keySet();
        admins.stream()
            .map(UserEntity::getUserId)
            .filter(connectedUserIds::contains)
            .forEach(userId -> sseEmitterService.sendToClient(userId, "dashboard_update", latestMetrics));
    }
    
    @Async
    @EventListener
    public void handleUserJoinedEvent(UserJoinedEvent event) {
        triggerDashboardUpdate();
    }
    
    @Async
    @EventListener
    public void handleDealMadeEvent(DealMadeEvent event) {
        triggerDashboardUpdate();
    }
}