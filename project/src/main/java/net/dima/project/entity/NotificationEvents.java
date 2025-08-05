package net.dima.project.entity;

import lombok.Getter;
import net.dima.project.entity.OfferEntity;
import org.springframework.context.ApplicationEvent;
import java.util.List;

/**
 * 시스템 내 알림 관련 이벤트를 정의하는 클래스 모음
 */
public class NotificationEvents {

    /**
     * 신규 제안(입찰)이 생성되었을 때 발행되는 이벤트
     */
    @Getter
    public static class OfferCreatedEvent extends ApplicationEvent {
        private final OfferEntity offer;
        public OfferCreatedEvent(Object source, OfferEntity offer) {
            super(source);
            this.offer = offer;
        }
    }

    /**
     * 제안이 확정(낙찰/거절)되었을 때 발행되는 이벤트
     */
    @Getter
    public static class OfferConfirmedEvent extends ApplicationEvent {
        private final List<OfferEntity> offers; // 관련된 모든 제안 (낙찰 1건, 거절 N건)
        private final OfferEntity winningOffer; // 낙찰된 제안
        public OfferConfirmedEvent(Object source, List<OfferEntity> offers, OfferEntity winningOffer) {
            super(source);
            this.offers = offers;
            this.winningOffer = winningOffer;
        }
    }

    /**
     * 컨테이너 상태가 변경되었을 때 발행되는 이벤트
     */
    @Getter
    public static class ContainerStatusChangedEvent extends ApplicationEvent {
        private final ContainerEntity container;
        private final String message; // 예: "컨테이너가 확정되었습니다."
        public ContainerStatusChangedEvent(Object source, ContainerEntity container, String message) {
            super(source);
            this.container = container;
            this.message = message;
        }
    }
}