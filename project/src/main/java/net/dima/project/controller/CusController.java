// [✅ 이 코드로 CusController.java 파일 전체를 교체해주세요]

package net.dima.project.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import net.dima.project.service.RequestService;
import net.dima.project.service.TransactionHistoryService;
import net.dima.project.dto.MyPostedRequestDto;
import net.dima.project.dto.TransactionHistoryDto;

import org.springframework.data.domain.Sort;

@Controller
@RequestMapping("/cus")
@RequiredArgsConstructor
@Slf4j
public class CusController {

    private final RequestService requestService; 
    private final TransactionHistoryService transactionHistoryService;
    
 // [✅ cusRequest 메서드를 아래 코드로 교체해주세요]
    // [수정] '나의요청 관리'는 이제 '제안받는중(OPEN)' 상태만 조회합니다.
    @GetMapping("/cusRequest")
    public String cusRequest(Model model, Authentication authentication,
                             @RequestParam(name = "excludeClosed", defaultValue = "true") boolean excludeClosed,
                             @RequestParam(name = "itemName", required = false) String itemName,
                             @PageableDefault(size = 5, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        
        String userId = authentication.getName();
        // status를 "OPEN"으로 고정하여 '제안받는중'인 요청만 가져옵니다.
        Page<MyPostedRequestDto> requestPage = requestService.getRequestsForShipper(userId, "OPEN", excludeClosed, itemName, pageable);

        model.addAttribute("requestPage", requestPage);
        model.addAttribute("activeMenu", "cusRequest");
        model.addAttribute("excludeClosed", excludeClosed);
        model.addAttribute("itemName", itemName);
        
        // [추가] 현재 정렬 상태를 View에 전달하는 코드
        String currentSortField = pageable.getSort().iterator().next().getProperty();
        String currentSortDirection = pageable.getSort().iterator().next().getDirection().name();
        model.addAttribute("currentSortField", currentSortField);
        model.addAttribute("reverseSortDirection", "ASC".equalsIgnoreCase(currentSortDirection) ? "desc" : "asc");
        
        return "cus/CUS_request";
    }

    
    // [추가] '운송중인 화물' 페이지
    @GetMapping("/tracking")
    public String cusTracking(Model model, Authentication authentication,
                              @RequestParam(name = "status", required = false) String status,
                              @RequestParam(name = "itemName", required = false) String itemName,
                              @PageableDefault(size = 5, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        
        String userId = authentication.getName();
        // status 파라미터가 없으면 'CLOSED' 상태의 모든 요청을 가져옵니다.
        String searchStatus = (status == null || status.isEmpty()) ? "CLOSED" : status;
        Page<MyPostedRequestDto> requestPage = requestService.getRequestsForShipper(userId, searchStatus, false, itemName, pageable);

        model.addAttribute("requestPage", requestPage);
        model.addAttribute("activeMenu", "cusTracking");
        model.addAttribute("status", status);
        model.addAttribute("itemName", itemName);

        return "cus/CUS_tracking";
    }

    /**
     * [추가] 화주의 요청 이력 조회 페이지를 반환하는 메소드
     */
    @GetMapping("/cusHistory")
    public String cusHistoryPage(Model model, Authentication authentication,
                                 // [✅ 추가] 날짜와 검색어 파라미터를 받도록 추가
                                 @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                 @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                 @RequestParam(name = "keyword", required = false) String keyword,
                                 @PageableDefault(size = 10, sort = "transactionDate", direction = Sort.Direction.DESC) Pageable pageable) {
        
        String userId = authentication.getName();
        // [✅ 수정] 서비스 호출 시 파라미터 전달
        Page<TransactionHistoryDto> historyPage = transactionHistoryService.getShipperHistory(userId, startDate, endDate, keyword, pageable);

        model.addAttribute("historyPage", historyPage);
        model.addAttribute("activeMenu", "cusHistory");
        
        // [✅ 추가] 뷰에서 사용할 수 있도록 파라미터 다시 전달
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("keyword", keyword);

        return "cus/CUS_history";
    }
}