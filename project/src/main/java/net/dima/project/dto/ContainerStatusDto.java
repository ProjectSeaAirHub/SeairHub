// [✅ /dto/ContainerStatusDto.java 파일 전체를 이 최종 코드로 교체해주세요]
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
    private Double totalCapacity;
    private String sailingDate;
    private String arrivalDate;
    private String route;
    private String departurePort; // [✅ 추가] 출발항 정보를 담을 필드
    private String arrivalPort;   // [✅ 추가] 도착항 정보를 담을 필드

    // 계산된 CBM 정보
    private Double confirmedCbm;
    private Double resaleCbm;
    private Double biddingCbm;
    private Double availableCbm;
    
    
    private ContainerStatus status;
    private boolean isDeletable;
    private boolean isConfirmable;

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
    
    public static ContainerStatusDto fromEntity(ContainerEntity entity) {
        return ContainerStatusDto.builder()
                .containerId(entity.getContainerId())
                .size(entity.getSize())
                .totalCapacity(entity.getCapacityCbm())
                .sailingDate(entity.getEtd().format(DateTimeFormatter.ofPattern("yyyy. M. d.")))
                .arrivalDate(entity.getEta().format(DateTimeFormatter.ofPattern("yyyy. M. d.")))
                .route(entity.getDeparturePort() + " → " + entity.getArrivalPort())
                .departurePort(entity.getDeparturePort()) // [✅ 추가] 출발항 정보 설정
                .arrivalPort(entity.getArrivalPort())   // [✅ 추가] 도착항 정보 설정
                .build();
    }
}