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
    // [✅ 수정] forwarderCompanyName 필드를 삭제하고, 아래 2개 필드를 추가합니다.
    private String departurePort;
    private String arrivalPort;
    private String containerId;
    private BigDecimal price;
    private String currency;
    private LocalDate etd;
    private LocalDate eta;

    public static OfferDto fromEntity(OfferEntity offer) {
        return OfferDto.builder()
                .offerId(offer.getOfferId())
                .departurePort(offer.getRequest().getDeparturePort()) // 요청 객체에서 출발항 정보 가져오기
                .arrivalPort(offer.getRequest().getArrivalPort())   // 요청 객체에서 도착항 정보 가져오기
                .containerId(offer.getContainer().getContainerId())
                .price(offer.getPrice())
                .currency(offer.getCurrency())
                .etd(offer.getContainer().getEtd())
                .eta(offer.getContainer().getEta())
                .build();
    }
}