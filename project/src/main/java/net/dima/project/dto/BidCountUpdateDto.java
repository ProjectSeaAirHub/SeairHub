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
public class BidCountUpdateDto {
    private Long requestId;     // 어떤 요청에 대한 업데이트인지 식별
    private long bidderCount;   // 새로운 제안(입찰) 건수
}