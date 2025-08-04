package net.dima.project.dto;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class ExternalCargoDto {
    private String containerId;
    private String cargoName;
    private Double cbm;
    private BigDecimal price; // [신규] 확정 운임
    private String currency;    // [신규] 통화
}