package net.dima.project.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
//거래내역을 위한 DTO

@Data
@Builder
public class TransactionHistoryDto {
    private LocalDateTime transactionDate; // 거래일 (Offer 생성일 기준)
    private String type;                   // 거래 유형 ("판매" 또는 "구매")
    private String itemName;               // 품명
    private String partnerName;            // 거래 상대방 (회사 이름)
    private BigDecimal price;              // 거래 금액
    private String currency;               // 통화
    private String status;                 // 최종 상태 (운송완료 등)
    private String departurePort; // [✅ 추가]
    private String arrivalPort;   // [✅ 추가]
}