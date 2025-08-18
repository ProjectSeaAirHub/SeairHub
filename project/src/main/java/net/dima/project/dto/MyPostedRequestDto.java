// [✅ /dto/MyPostedRequestDto.java 파일 전체를 이 최종 코드로 교체해주세요]
package net.dima.project.dto;

import lombok.Builder;
import lombok.Data;
import net.dima.project.entity.ContainerStatus;
import net.dima.project.entity.OfferEntity;
import net.dima.project.entity.OfferStatus;
import net.dima.project.entity.RequestEntity;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class MyPostedRequestDto {
    private Long requestId;
    private String itemName;
    private Double cbm;
    private String deadline;
    private long bidderCount;
    private String status;
    private String winningBidderCompanyName;
    private String detailedStatus;
    private String detailedStatusText;
    private String imoNumber;
    private LocalDateTime deadlineDateTime;
    private LocalDate desiredArrivalDate;

    /**
     * '입찰 진행 중'인 요청을 위한 DTO 생성자 (주로 화주, 재판매 포워더가 사용)
     */
    public static MyPostedRequestDto fromEntity(RequestEntity entity, long bidderCount) {
        return MyPostedRequestDto.builder()
                .requestId(entity.getRequestId())
                .itemName(entity.getCargo().getItemName())
                .cbm(entity.getCargo().getTotalCbm())
                .deadline(entity.getDeadline().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                .deadlineDateTime(entity.getDeadline())
                .bidderCount(bidderCount)
                .status(entity.getStatus().name())
                .detailedStatus("OPEN")
                .detailedStatusText("입찰 진행 중")
                .desiredArrivalDate(entity.getDesiredArrivalDate())
                .build();
    }

    /**
     * '낙찰' 이후의 요청 상태를 표현하기 위한 DTO 생성자 (주로 화주가 사용)
     * @param entity 원본 요청
     * @param directPartnerOffer 직접적인 계약 상대방(B 포워더)의 제안
     * @param finalOfferInChain 실제 운송을 책임지는 최종 포워더(C 포워더)의 제안
     */
    public static MyPostedRequestDto fromEntity(RequestEntity entity, Optional<OfferEntity> directPartnerOffer, Optional<OfferEntity> finalOfferInChain) {

        // 최종 운송사의 컨테이너 상태를 기준으로 정보 공개 여부 결정
        boolean isConfirmed = finalOfferInChain.map(o -> o.getContainer().getStatus() != ContainerStatus.SCHEDULED).orElse(false);

        String partnerName = isConfirmed ?
                finalOfferInChain.map(offer -> offer.getForwarder().getCompanyName()).orElse("정보 없음") :
                "포워더 정보 불러오는 중";

        OfferStatus detailedStatus = finalOfferInChain.map(OfferEntity::getStatus).orElse(null);
        String imoNumber = finalOfferInChain.map(o -> o.getContainer().getImoNumber()).orElse(null);

        String statusText = "낙찰";
        if (detailedStatus != null) {
            switch (detailedStatus) {
                case CONFIRMED: statusText = "컨테이너 확정"; break;
                case SHIPPED: statusText = "선적완료"; break;
                case COMPLETED: statusText = "운송완료"; break;
                default: statusText = "낙찰"; // ACCEPTED, RESOLD 등
            }
        }

        return MyPostedRequestDto.builder()
                .requestId(entity.getRequestId())
                .itemName(entity.getCargo().getItemName())
                .cbm(entity.getCargo().getTotalCbm())
                .deadline(entity.getDeadline().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                .deadlineDateTime(entity.getDeadline())
                .status(entity.getStatus().name())
                .winningBidderCompanyName(partnerName) // 최종 운송사 정보 또는 "선정 중"
                .detailedStatus(detailedStatus != null ? detailedStatus.name() : "NONE")
                .detailedStatusText(statusText)
                .imoNumber(imoNumber)
                .desiredArrivalDate(entity.getDesiredArrivalDate())
                .build();
    }

    /**
     * 재판매 요청 관리 페이지를 위한 DTO 생성자 (중간 포워더 B가 사용)
     */
    public static MyPostedRequestDto fromEntity(RequestEntity entity, Optional<OfferEntity> winningOfferOpt) {
        
        String winningBidder = winningOfferOpt
                .map(offer -> offer.getForwarder().getCompanyName())
                .orElse("낙찰자 정보 없음");

        OfferStatus offerStatus = winningOfferOpt.map(OfferEntity::getStatus).orElse(null);

        String statusText = "낙찰";
        if (offerStatus != null) {
            switch (offerStatus) {
                case CONFIRMED: statusText = "컨테이너 확정"; break;
                case SHIPPED: statusText = "선적완료"; break;
                case COMPLETED: statusText = "운송완료"; break;
            }
        }
        
        String imoNumber = winningOfferOpt.map(o -> o.getContainer().getImoNumber()).orElse(null);

        return MyPostedRequestDto.builder()
                .requestId(entity.getRequestId())
                .itemName(entity.getCargo().getItemName())
                .cbm(entity.getCargo().getTotalCbm())
                .deadline(entity.getDeadline().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                .deadlineDateTime(entity.getDeadline())
                .status(entity.getStatus().name())
                .winningBidderCompanyName(winningBidder)
                .detailedStatus(offerStatus != null ? offerStatus.name() : "NONE")
                .detailedStatusText(statusText)
                .imoNumber(imoNumber)
                .desiredArrivalDate(entity.getDesiredArrivalDate())
                .build();
    }
}