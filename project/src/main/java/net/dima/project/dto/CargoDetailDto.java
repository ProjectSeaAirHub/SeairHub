package net.dima.project.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class CargoDetailDto {
    private Long offerId;
    private String itemName;
    private Double cbm;
    private BigDecimal freightCost;
    private String freightCurrency;
    private String status;
    private boolean external; 
    private LocalDateTime deadline;
    private Long resaleRequestId; // [✅ 추가] 재판매 요청 ID
}