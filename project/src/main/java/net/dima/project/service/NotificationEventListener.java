package net.dima.project.service;

import lombok.RequiredArgsConstructor;
import net.dima.project.entity.ContainerCargoEntity;
import net.dima.project.entity.NotificationEvents.*;
import net.dima.project.entity.OfferEntity;
import net.dima.project.entity.RequestEntity;
import net.dima.project.entity.UserEntity;
import net.dima.project.repository.ContainerCargoRepository;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;
    private final ChatService chatService; // ChatService를 주입받습니다.
    private final ContainerCargoRepository containerCargoRepository;

    /**
     * 신규 제안 생성 이벤트를 처리합니다.
     */
    @Async
    @EventListener
    public void handleOfferCreatedEvent(OfferCreatedEvent event) {
        OfferEntity offer = event.getOffer();
        RequestEntity request = offer.getRequest();
        UserEntity requester = request.getRequester(); // 요청을 올린 사람 (화주 또는 재판매 포워더)

        String itemName = request.getCargo().getItemName();
        String message = String.format("'%s' 요청에 새로운 제안이 도착했습니다.", itemName);
        String url = (requester.getRoles().contains("cus")) ? "/cus/cusRequest" : "/fwd/my-posted-requests";

        notificationService.sendNotification(requester, message, url);
    }

    /**
     * 제안 확정(낙찰/거절) 이벤트를 처리합니다.
     */
    @EventListener
    public void handleOfferConfirmedEvent(OfferConfirmedEvent event) {
        OfferEntity winningOffer = event.getWinningOffer();
        String itemName = winningOffer.getRequest().getCargo().getItemName();
        String url = "/fwd/my-offers";

        // 관련된 모든 제안을 순회하며 낙찰/거절 알림 발송
        for (OfferEntity offer : event.getOffers()) {
            if (offer.equals(winningOffer)) {
                // 낙찰자에게 알림
                String message = String.format("축하합니다! '%s' 제안이 낙찰되었습니다.", itemName);
                notificationService.sendNotification(offer.getForwarder(), message, url);
            } else {
                // 유찰자에게 알림
                String message = String.format("아쉽지만 '%s' 제안은 마감되었습니다.", itemName);
                notificationService.sendNotification(offer.getForwarder(), message, url);
            }
        }
     // [추가] 채팅방 생성 로직을 이곳으로 이동하여 함께 비동기 처리합니다.
        chatService.createChatRoomForOffer(winningOffer);
    }

    /**
     * 컨테이너 상태 변경 이벤트를 처리합니다.
     */
    @EventListener
    public void handleContainerStatusChangedEvent(ContainerStatusChangedEvent event) {
        String message = String.format("컨테이너 '%s'의 상태가 변경되었습니다: %s",
                event.getContainer().getContainerId(), event.getMessage());
        String cusUrl = "/cus/tracking";
        String fwdUrl = "/fwd/my-offers";

        // 이 컨테이너에 실린 모든 화물의 원 화주와 재판매 포워더를 중복 없이 찾는다.
        Set<UserEntity> receivers = new HashSet<>();
        
        // isExternal=false인 화물만 조회 (플랫폼 거래 건)
        List<ContainerCargoEntity> cargos = containerCargoRepository.findExternalCargosByContainerId(event.getContainer().getContainerId(), false);

        for (ContainerCargoEntity cargo : cargos) {
            OfferEntity offer = cargo.getOffer();
            if (offer != null) {
                RequestEntity request = offer.getRequest();
                // 원본 요청의 화주 (최초 화주)
                receivers.add(request.getCargo().getOwner());
                // 재판매 요청의 요청자 (중간 포워더)
                if (request.getSourceOffer() != null) {
                    receivers.add(request.getRequester());
                }
            }
        }

        // 각 수신자에게 역할에 맞는 URL로 알림 전송
        for (UserEntity receiver : receivers) {
            String finalUrl = receiver.getRoles().contains("cus") ? cusUrl : fwdUrl;
            notificationService.sendNotification(receiver, message, finalUrl);
        }
    }
}