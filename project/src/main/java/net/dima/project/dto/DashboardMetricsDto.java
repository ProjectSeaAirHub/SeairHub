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
}