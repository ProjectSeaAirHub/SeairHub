package net.dima.project.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class UpdateOfferDto {
    private BigDecimal price;
    private String currency;
}