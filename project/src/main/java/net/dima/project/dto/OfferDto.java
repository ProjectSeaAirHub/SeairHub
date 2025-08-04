package net.dima.project.dto;

import lombok.Builder;
import lombok.Data;
import net.dima.project.entity.OfferEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class OfferDto {
    private Long offerId;
    private String forwarderCompanyName;
    private String containerId;
    private BigDecimal price;
    private String currency;
    private LocalDate etd;
    private LocalDate eta;

    public static OfferDto fromEntity(OfferEntity offer) {
        return OfferDto.builder()
                .offerId(offer.getOfferId())
                .forwarderCompanyName(offer.getForwarder().getCompanyName())
                .containerId(offer.getContainer().getContainerId())
                .price(offer.getPrice())
                .currency(offer.getCurrency())
                .etd(offer.getContainer().getEtd())
                .eta(offer.getContainer().getEta())
                .build();
    }
}