package net.dima.project.dto;

import lombok.Data;

import java.math.BigDecimal;

// JS에서 보낸 제안 생성 요청을 받는 DTO
@Data
public class OfferRequestDto {
    private Long requestId;
    private String containerId;
    private BigDecimal price;
    private String currency;
}