package net.dima.project.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dima.project.entity.RequestEntity;
import net.dima.project.repository.RequestRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * [✅ 핵심 추가] 주기적으로 실행되어야 하는 작업들을 관리하는 스케줄러
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RequestScheduler {

    private final RequestRepository requestRepository;
    private final ResaleService resaleService;

    /**
     * 매 시간 정각에 실행되어, 마감 기한이 지난 재판매 요청을 자동으로 처리합니다.
     * cron = "초 분 시 일 월 요일"
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void processExpiredResaleRequests() {
        log.info("===== [스케줄러 실행] 마감된 재판매 요청 처리 시작 =====");
        
        // 1. Repository에서 마감된 재판매 요청 목록을 조회합니다.
        List<RequestEntity> expiredRequests = requestRepository.findExpiredResaleRequests(LocalDateTime.now());

        if (expiredRequests.isEmpty()) {
            log.info(">> 처리할 마감된 재판매 요청이 없습니다.");
            return;
        }

        log.info(">> 처리 대상 재판매 요청 {}건 발견", expiredRequests.size());
        
        // 2. 각 요청에 대해 ResaleService의 공통 로직을 호출하여 상태를 되돌립니다.
        for (RequestEntity request : expiredRequests) {
            log.info("   - 요청 ID {} 처리 중...", request.getRequestId());
            resaleService.revertResaleRequest(request);
        }
        
        log.info("===== [스케줄러 종료] 마감된 재판매 요청 처리 완료 =====");
    }
}