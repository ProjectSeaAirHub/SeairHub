// [✅ NotificationEventListener.java 파일 전체를 이 최종 코드로 교체해주세요]
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
import net.dima.project.repository.OfferRepository;

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
import net.dima.project.dto.OfferStatusUpdateDto;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;
    private final SseEmitterService sseEmitterService;
    private final UserRepository userRepository;
    private final OfferRepository offerRepository;
    private final AdminService adminService;

    /**
     * 신규 제안 생성 시, 요청자에게 실시간 알림과 UI 업데이트를 보냅니다.
     */
    @Async
    @EventListener
    public void handleOfferCreatedEvent(OfferCreatedEvent event) {
        OfferEntity offer = event.getOffer();
        RequestEntity request = offer.getRequest();
        UserEntity requester = request.getRequester();
        String itemName = request.getCargo().getItemName();

        // 1. 요청자에게 텍스트 알림 전송
        String message = String.format("'%s' 요청에 새로운 제안이 도착했습니다.", itemName);
        String url = (requester.getRoles().contains("cus")) ? "/cus/cusRequest" : "/fwd/my-posted-requests";
        notificationService.sendNotification(requester, message, url);

        // 2. 요청자의 화면에 제안 건수를 실시간으로 업데이트하기 위한 SSE 이벤트 전송
        long newBidderCount = offerRepository.countByRequest(request);
        BidCountUpdateDto updateDto = BidCountUpdateDto.builder()
                .requestId(request.getRequestId())
                .bidderCount(newBidderCount)
                .build();
        sseEmitterService.sendToClient(requester.getUserId(), "bid_count_update", updateDto);
    }

    /**
     * 제안 확정(낙찰/거절) 시, 각 포워더에게 결과를 알립니다.
     */
    @Async
    @EventListener
    public void handleOfferConfirmedEvent(OfferConfirmedEvent event) {
        OfferEntity winningOffer = event.getWinningOffer();
        String itemName = winningOffer.getRequest().getCargo().getItemName();
        String url = "/fwd/my-offers";

        for (OfferEntity offer : event.getOffers()) {
            UserEntity forwarder = offer.getForwarder();
            OfferStatusUpdateDto updateDto;
            String message;

            if (offer.equals(winningOffer)) {
                message = String.format("축하합니다! '%s' 제안이 낙찰되었습니다.", itemName);
                updateDto = OfferStatusUpdateDto.builder().offerId(offer.getOfferId()).status(OfferStatus.ACCEPTED.name()).statusText("수락").build();
            } else {
                message = String.format("아쉽지만 '%s' 제안은 마감되었습니다.", itemName);
                updateDto = OfferStatusUpdateDto.builder().offerId(offer.getOfferId()).status(OfferStatus.REJECTED.name()).statusText("거절").build();
            }
            
            notificationService.sendNotification(forwarder, message, url);
            sseEmitterService.sendToClient(forwarder.getUserId(), "offer_status_update", updateDto);
        }
        triggerDashboardUpdate();
    }

    /**
     * 컨테이너 상태 변경 시, 관련된 모든 사용자에게 알림 및 UI 업데이트를 보냅니다.
     */
    @Async
    @EventListener
    public void handleContainerStatusChangedEvent(ContainerStatusChangedEvent event) {
        ContainerEntity container = event.getContainer();
        String message = String.format("컨테이너 '%s'의 상태가 변경되었습니다: %s",
                container.getContainerId(), event.getMessage());
        String cusUrl = "/cus/tracking";
        String fwdUrl = "/fwd/my-posted-requests";

        Set<UserEntity> receivers = new HashSet<>();
        List<OfferEntity> offersInContainer = offerRepository.findAllByContainer(container);

        for (OfferEntity offer : offersInContainer) {
            RequestEntity initialRequest = offer.getRequest();
            if (initialRequest == null) continue;

            // 재판매 체인을 따라 올라가며 모든 관련자를 receivers에 추가
            RequestEntity currentRequest = initialRequest;
            while (currentRequest != null) {
                UserEntity requester = currentRequest.getRequester();
                receivers.add(requester);

                // 실시간 UI 업데이트를 위한 SSE 이벤트 전송
                ShipmentStatusUpdateDto updateDto = ShipmentStatusUpdateDto.builder()
                        .requestId(currentRequest.getRequestId())
                        .detailedStatus(container.getStatus().name())
                        .build();
                sseEmitterService.sendToClient(requester.getUserId(), "shipment_update", updateDto);

                currentRequest = (currentRequest.getSourceOffer() != null) ?
                                 currentRequest.getSourceOffer().getRequest() :
                                 null;
            }
            receivers.add(initialRequest.getCargo().getOwner()); // 원본 화주 추가
        }

        // 수집된 모든 관련자에게 역할에 맞는 URL로 텍스트 알림 전송
        for (UserEntity receiver : receivers) {
            if (!receiver.getUserSeq().equals(container.getForwarder().getUserSeq())) {
                String finalUrl = receiver.getRoles().contains("cus") ? cusUrl : fwdUrl;
                notificationService.sendNotification(receiver, message, finalUrl);
            }
        }
    }

    /**
     * 신규 화물 요청 생성 시, 접속 중인 모든 포워더에게 SSE 이벤트를 보냅니다.
     */
    @Async
    @EventListener
    public void handleRequestCreatedEvent(RequestCreatedEvent event) {
        RequestCardDto newRequestDto = event.getRequestCardDto();
        List<UserEntity> forwarders = userRepository.findByRolesIn(List.of("ROLE_fwd"));
        Set<String> connectedUserIds = sseEmitterService.getEmitters().keySet();

        forwarders.stream()
            .map(UserEntity::getUserId)
            .filter(connectedUserIds::contains)
            .forEach(userId -> sseEmitterService.sendToClient(userId, "new_request", newRequestDto));
        
        triggerDashboardUpdate();
    }

    /**
     * 관리자 대시보드 실시간 업데이트를 트리거하는 헬퍼 메서드
     */
    private void triggerDashboardUpdate() {
        DashboardMetricsDto latestMetrics = adminService.getDashboardMetrics();
        List<UserEntity> admins = userRepository.findByRolesIn(List.of("ROLE_admin"));
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