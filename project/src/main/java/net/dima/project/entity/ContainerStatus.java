package net.dima.project.entity;

public enum ContainerStatus {
    SCHEDULED,  // 등록됨 (수정/삭제 가능)
    CONFIRMED,  // 확정됨 (선적 대기)
    SHIPPED,    // 선적완료 (운송중)
    COMPLETED,   // 운송완료
    SETTLED     // [✅ 추가] 정산완료 (목록에서 숨김)
}