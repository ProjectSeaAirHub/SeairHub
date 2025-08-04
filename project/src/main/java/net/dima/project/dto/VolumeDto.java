package net.dima.project.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VolumeDto {
    private double confirmedCbm; // 확정 (화주 수락, 컨테이너 확정 전)
    private double resaleCbm;    // 재판매중
    private double biddingCbm;   // 입찰중
    private double availableCbm; // 공차물량
}