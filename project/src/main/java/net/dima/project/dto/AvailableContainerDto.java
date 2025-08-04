package net.dima.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate; 

// JS로 보낼, 제안 가능한 컨테이너의 정보를 담는 DTO
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailableContainerDto {
    private String containerId;
    private String containerDisplayName; // 예: "SEAU1111111 (부산 -> 상해)"
    private Double availableCbm;
    private LocalDate etd; 
    private LocalDate eta;
}