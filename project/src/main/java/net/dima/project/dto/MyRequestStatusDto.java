package net.dima.project.dto;

import lombok.Builder;
import lombok.Data;
import net.dima.project.entity.OfferEntity;
import net.dima.project.entity.OfferStatus;
import net.dima.project.entity.RequestEntity;

import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Data
@Builder
public class MyRequestStatusDto {
    private String itemName;
    private Double cbm;
    private String deadline;
    private String finalForwarderName; // 최종 운송사 이름
    private String detailedStatus;
    private String detailedStatusText;

    public static MyRequestStatusDto fromEntity(RequestEntity request, Optional<OfferEntity> finalOfferOpt) {
        
        String forwarderName = finalOfferOpt.map(offer -> offer.getForwarder().getCompanyName()).orElse("선정중");
        OfferStatus status = finalOfferOpt.map(OfferEntity::getStatus).orElse(null);

        String statusText = "낙찰";
        if (status != null) {
            switch (status) {
                case CONFIRMED: statusText = "컨테이너 확정"; break;
                case SHIPPED: statusText = "선적완료"; break;
                case COMPLETED: statusText = "운송완료"; break;
                case PENDING: statusText = "입찰 진행중"; break;
                default: statusText = "낙찰";
            }
        } else {
            statusText = "입찰 진행중";
        }

        return MyRequestStatusDto.builder()
                .itemName(request.getCargo().getItemName())
                .cbm(request.getCargo().getTotalCbm())
                .deadline(request.getDeadline().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                .finalForwarderName(forwarderName)
                .detailedStatus(status != null ? status.name() : "OPEN")
                .detailedStatusText(statusText)
                .build();
    }
}