package net.dima.project.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class NewRequestDto {
    // 화물 정보
    private String itemName;
    private String incoterms;
    private Double totalCbm;

    // 요청 정보
    private String departurePort;
    private String arrivalPort;
    private LocalDateTime deadline;
    private LocalDate desiredArrivalDate;
    private String tradeType;
    private String transportType;
}