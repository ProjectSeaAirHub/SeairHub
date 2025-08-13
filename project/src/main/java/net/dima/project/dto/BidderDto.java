// [✅ /dto/BidderDto.java 파일 전체를 이 최종 코드로 교체해주세요]
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
    // [✅ 수정] bidderCompanyName 필드를 삭제하고, 아래 2개 필드를 추가합니다.
    private String departurePort;
    private String arrivalPort;
    private String containerId;
    private BigDecimal price;
    private String currency;
    private LocalDate etd;
    private LocalDate eta;

    // [✅ 수정] fromEntity 메서드가 새로운 필드를 담도록 변경합니다.
    public static BidderDto fromEntity(OfferEntity offer) {
        return BidderDto.builder()
                .offerId(offer.getOfferId())
                .departurePort(offer.getRequest().getDeparturePort())
                .arrivalPort(offer.getRequest().getArrivalPort())
                .containerId(offer.getContainer().getContainerId())
                .price(offer.getPrice())
                .currency(offer.getCurrency())
                .etd(offer.getContainer().getEtd())
                .eta(offer.getContainer().getEta())
                .build();
    }
}