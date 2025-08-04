package net.dima.project.dto;

import lombok.Builder;
import lombok.Data;
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
    private String status; // [✅ 추가] 요청의 현재 상태
    private String winningBidderCompanyName; // [✅ 추가] 낙찰자 회사 이름
    private String detailedStatus; // [✅ 추가] 낙찰 이후의 상세 진행 상태 (OfferStatus)
    private String detailedStatusText; // [✅ 추가] 화면에 표시될 텍스트
    private LocalDateTime deadlineDateTime; // [✅ 이 필드를 추가해주세요]
    private LocalDate desiredArrivalDate; // [✅ 이 줄을 추가해주세요]

    // [✅ 수정] fromEntity 메서드 시그니처 변경
 // [✅ 기존의 fromEntity 메서드 2개를 아래 코드로 교체해주세요]

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
                // ACCEPTED, RESOLD 등은 기본값 "낙찰"을 사용
            }
        }

        return MyPostedRequestDto.builder()
                .requestId(entity.getRequestId())
                .itemName(entity.getCargo().getItemName())
                .cbm(entity.getCargo().getTotalCbm())
                .deadline(entity.getDeadline().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                .deadlineDateTime(entity.getDeadline()) // [✅ 추가]
                .bidderCount(0) // 낙찰 후에는 입찰자 수가 중요하지 않으므로 0으로 통일
                .status(entity.getStatus().name())
                .winningBidderCompanyName(winningBidder)
                .detailedStatus(offerStatus != null ? offerStatus.name() : "NONE")
                .detailedStatusText(statusText)
                .desiredArrivalDate(entity.getDesiredArrivalDate()) 
                .build();
    }

    // [✅ fromEntity(RequestEntity entity, long bidderCount) 메서드를 수정]
    public static MyPostedRequestDto fromEntity(RequestEntity entity, long bidderCount) {
        return MyPostedRequestDto.builder()
                .requestId(entity.getRequestId())
                .itemName(entity.getCargo().getItemName())
                .cbm(entity.getCargo().getTotalCbm())
                .deadline(entity.getDeadline().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                .deadlineDateTime(entity.getDeadline()) // [✅ 추가]
                .bidderCount(bidderCount)
                .status(entity.getStatus().name())
                .winningBidderCompanyName("")
                .detailedStatus("OPEN")
                .detailedStatusText("입찰 진행 중")
                .desiredArrivalDate(entity.getDesiredArrivalDate())
                .build();
    }
}