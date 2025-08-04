package net.dima.project.dto;

import lombok.Builder;
import lombok.Data;
import net.dima.project.entity.OfferEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import net.dima.project.entity.OfferStatus;

@Data
@Builder
public class MyOfferDetailDto {

    // 화물 정보
    private String itemName; // [✅ 이 줄을 추가해주세요]
    private Double cbm;

    // 화주(요청자) 정보
    private String requesterCompanyName;

    // 요청 정보
    private String deadline;

    // 내 제안 정보
    private BigDecimal myOfferPrice;
    private String myOfferCurrency;
    private String containerId;
    
    // [✅ 2. 최종 낙찰 정보를 담을 필드 추가]
    private BigDecimal finalPrice;
    private String finalCurrency;
    private boolean closedWithoutWinner;


    // [✅ 3. fromEntity 메서드를 아래 코드로 교체해주세요]
    // [✅ 2. fromEntity 메서드를 아래 코드로 교체해주세요]
    public static MyOfferDetailDto fromEntity(OfferEntity offer, Optional<OfferEntity> winningOfferOpt) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        MyOfferDetailDtoBuilder builder = MyOfferDetailDto.builder()
                .itemName(offer.getRequest().getCargo().getItemName())
                .cbm(offer.getRequest().getCargo().getTotalCbm())
                .deadline(offer.getRequest().getDeadline().format(formatter))
                .myOfferPrice(offer.getPrice())
                .myOfferCurrency(offer.getCurrency())
                .containerId(offer.getContainer().getContainerId());
        
        // [핵심] 제안 상태가 '수락' 또는 '재판매중'일 때만 화주 정보를 DTO에 담습니다.
        OfferStatus status = offer.getStatus();
        if (status == OfferStatus.ACCEPTED || status == OfferStatus.FOR_SALE) {
            builder.requesterCompanyName(offer.getRequest().getRequester().getCompanyName());
        }
        
        // 최종 낙찰 정보를 DTO에 담는 로직
        winningOfferOpt.ifPresentOrElse(
            winningOffer -> {
                builder.finalPrice(winningOffer.getPrice());
                builder.finalCurrency(winningOffer.getCurrency());
                builder.closedWithoutWinner(false);
            },
            () -> {
                builder.closedWithoutWinner(true);
            }
        );

        return builder.build();
    }
}