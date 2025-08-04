package net.dima.project.entity;

public enum OfferStatus {
    PENDING,    // 진행중
    ACCEPTED,   // 수락 (재판매 요청에서 낙찰된 상태)
    REJECTED,   // 거절
    FOR_SALE,   // 재판매중
    RESOLD,     // 재판매 완료 (원본 제안의 최종 상태)
    CONFIRMED,  // 컨테이너 확정
    SHIPPED,    // 선적완료
    COMPLETED   // 운송완료
}