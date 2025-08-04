package net.dima.project.dto;

import lombok.Builder;
import lombok.Data;
import net.dima.project.entity.ContainerEntity;
import net.dima.project.entity.ContainerStatus;

import java.time.format.DateTimeFormatter;

@Data
@Builder
public class ContainerStatusDto {
    // 컨테이너 기본 정보
    private String containerId;
    private String size;
    private Double totalCapacity; // 총 용량
    private String sailingDate;   // 출항일
    private String arrivalDate;
    private String route;         // 경로

    // 계산된 CBM 정보
    private Double confirmedCbm;
    private Double resaleCbm;
    private Double biddingCbm;
    private Double availableCbm;
    
    
    private ContainerStatus status;
    private boolean isDeletable;   // [✅ 추가] 삭제 가능 여부
    private boolean isConfirmable; // [✅ 추가] 확정 가능 여부

    // 프로그레스 바를 위한 퍼센트 계산 메서드
    public int getConfirmedPercent() {
        if (totalCapacity == 0) return 0;
        return (int) Math.round((confirmedCbm / totalCapacity) * 100);
    }

    public int getResalePercent() {
        if (totalCapacity == 0) return 0;
        return (int) Math.round((resaleCbm / totalCapacity) * 100);
    }

    public int getBiddingPercent() {
        if (totalCapacity == 0) return 0;
        return (int) Math.round((biddingCbm / totalCapacity) * 100);
    }
    
    // Entity를 DTO로 변환하는 정적 헬퍼 메서드
    public static ContainerStatusDto fromEntity(ContainerEntity entity) {
        return ContainerStatusDto.builder()
                .containerId(entity.getContainerId())
                .size(entity.getSize())
                .totalCapacity(entity.getCapacityCbm())
                .sailingDate(entity.getEtd().format(DateTimeFormatter.ofPattern("yyyy. M. d.")))
                .arrivalDate(entity.getEta().format(DateTimeFormatter.ofPattern("yyyy. M. d.")))
                .route(entity.getDeparturePort() + " → " + entity.getArrivalPort())
                .build();
    }
}