package net.dima.project.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Data;

@Data
public class ScfiDataDto {
    private LocalDate recordDate;
    private BigDecimal indexValue;
}
