// [✅ 이 전체 코드로 새 파일을 생성해주세요]
package net.dima.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfferStatusUpdateDto {
    private Long offerId;
    private String status;      // "ACCEPTED", "REJECTED" 등 Enum 이름
    private String statusText;  // "수락", "거절" 등 화면에 표시될 텍스트
}