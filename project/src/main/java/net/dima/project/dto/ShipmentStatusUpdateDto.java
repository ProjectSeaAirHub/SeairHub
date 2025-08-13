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
public class ShipmentStatusUpdateDto {
    private Long requestId;         // 상태가 변경된 요청의 ID
    private String detailedStatus;  // 변경된 상세 상태 (예: "SHIPPED", "COMPLETED")
}