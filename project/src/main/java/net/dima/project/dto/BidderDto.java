package net.dima.project.dto;

import lombok.Builder;
import lombok.Data;
import net.dima.project.entity.OfferEntity;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class BidderDto {
    private Long offerId;
    private String bidderCompanyName;
    private String containerId;
    private BigDecimal price;
    private String currency;
    private LocalDate etd; // [✅ 추가]
    private LocalDate eta; // [✅ 추가]

    public static BidderDto fromEntity(OfferEntity offer) {
        return BidderDto.builder()
                .offerId(offer.getOfferId())
                .bidderCompanyName(offer.getForwarder().getCompanyName())
                .containerId(offer.getContainer().getContainerId())
                .price(offer.getPrice())
                .currency(offer.getCurrency())
                .etd(offer.getContainer().getEtd()) // [✅ 추가]
                .eta(offer.getContainer().getEta()) // [✅ 추가]
                .build();
    }
}