package net.dima.project.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardMetricsDto {
    // 실시간 거래 현황
    private long todayRequests;
    private long todayDeals;

    // 사용자 현황
    private long totalFwdUsers;
    private long totalCusUsers;

    // 주요 알림
    private long pendingUsers;
    private long noBidRequests;
    
    // [추가] SCFI 분석 정보
    private Double scfiChangePercentage; // 등락률
    private String scfiStatus;           // 상태 (RED, GREEN, NORMAL)
    
    // [✅ 아래 필드를 추가해주세요]
    private Double missedConfirmationRate; // 화주 미확정 마감 비율
}