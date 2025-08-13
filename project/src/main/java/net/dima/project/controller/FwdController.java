package net.dima.project.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dima.project.dto.ContainerStatusDto;
import net.dima.project.dto.MyOfferDto;
import net.dima.project.dto.MyPostedRequestDto;
import net.dima.project.dto.RequestCardDto;
import net.dima.project.service.ContainerService;
import net.dima.project.service.OfferService;
import net.dima.project.service.RequestService;
import net.dima.project.service.ResaleService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.data.domain.Sort;

import java.util.List;

@Controller
@RequestMapping("/fwd")
@RequiredArgsConstructor
@Slf4j
public class FwdController {

    // [✅ 수정] FwdService 대신 새로운 서비스들을 주입
    private final RequestService requestService;
    private final OfferService offerService;
    private final ContainerService containerService;
    private final ResaleService resaleService; 

    /**
     * 포워더 대시보드 (견적요청조회) 페이지
     */
    // [✅ 교체] fwdRequest 메서드를 아래 코드로 교체합니다.
 // [✅ 이 메서드 전체를 아래 코드로 교체해주세요]
    @GetMapping("/fwdRequest")
    public String fwdRequest(Model model, Authentication authentication,
                             @RequestParam(name = "excludeClosed", defaultValue = "true") boolean excludeClosed,
                             @RequestParam(name = "tradeType", required = false) String tradeType,
                             @RequestParam(name = "transportType", required = false) String transportType,
                             @RequestParam(name = "departurePort", required = false) String departurePort,
                             @RequestParam(name = "arrivalPort", required = false) String arrivalPort,
                             @RequestParam(name = "itemName", required = false) String itemName,
                             @PageableDefault(size = 10, sort = {"createdAt"}, direction = Sort.Direction.DESC) Pageable pageable) {

        String userId = authentication.getName();
        Page<RequestCardDto> requestPage = requestService.getRequests(
                excludeClosed, tradeType, transportType, departurePort, arrivalPort, itemName, pageable, userId);

        model.addAttribute("requestsPage", requestPage);
        model.addAttribute("activeMenu", "fwdRequest");
        
        model.addAttribute("excludeClosed", excludeClosed);
        model.addAttribute("tradeType", tradeType);
        model.addAttribute("transportType", transportType);
        model.addAttribute("departurePort", departurePort);
        model.addAttribute("arrivalPort", arrivalPort);
        model.addAttribute("itemName", itemName);

        String currentSortField = pageable.getSort().iterator().next().getProperty();
        String currentSortDirection = pageable.getSort().iterator().next().getDirection().name();
        model.addAttribute("currentSortField", currentSortField);
        model.addAttribute("currentSortDirection", currentSortDirection); // [✅ 이 줄 추가]
        model.addAttribute("reverseSortDirection", currentSortDirection.equals("ASC") ? "desc" : "asc");

        return "fwd/FWD_request";}

    /**
     * 포워더 - 나의 요청 조회 페이지
     */
    @GetMapping("/my-posted-requests")
    public String fwdMyPostedRequests(Model model, Authentication authentication,
                                      @RequestParam(name = "status", required = false) String status,
                                      @PageableDefault(size = 5, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        
        String userId = authentication.getName();
        Page<MyPostedRequestDto> myRequestsPage = resaleService.getMyPostedRequests(userId, status, pageable);

        model.addAttribute("myRequestsPage", myRequestsPage);
        model.addAttribute("activeMenu", "my-posted-requests");
        model.addAttribute("status", status);

        return "fwd/FWD_my_posted_requests";
    }

    /**
     * 포워더 - 나의 제안 조회 페이지
     */
    @GetMapping("/my-offers")
    public String fwdMyOffers(Model model, Authentication authentication,
                              @RequestParam(name = "status", required = false) String status,
                              @RequestParam(name = "keyword", required = false) String keyword,
                              @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        String userId = authentication.getName();
        
        // 서비스 호출 시 새로운 파라미터 전달
        Page<MyOfferDto> myOffersPage = offerService.getMyOffers(userId, status, keyword, pageable);

        model.addAttribute("myOffersPage", myOffersPage);
        model.addAttribute("activeMenu", "my-offers");

        // [✅ 추가] 뷰에서 사용할 수 있도록 파라미터와 정렬 정보를 모델에 추가
        model.addAttribute("status", status);
        model.addAttribute("keyword", keyword);

        String currentSortField = pageable.getSort().isSorted() ? pageable.getSort().iterator().next().getProperty() : "createdAt";
        String currentSortDirection = pageable.getSort().isSorted() ? pageable.getSort().iterator().next().getDirection().name() : "DESC";
        model.addAttribute("currentSortField", currentSortField);
        model.addAttribute("currentSortDirection", currentSortDirection); // [✅ 이 줄 추가]
        model.addAttribute("reverseSortDirection", currentSortDirection.equalsIgnoreCase("ASC") ? "desc" : "asc");
        
        return "fwd/FWD_my_offers";
    }

    /**
     * 포워더 - 컨테이너 조회 페이지
     */
    @GetMapping("/container-inquiry")
    public String fwdContainerInquiry(Model model, Authentication authentication,
                                      // [✅ Pageable 파라미터 추가, 기본 정렬은 등록일순(id)으로 설정]
                                      @PageableDefault(size = 10, sort = "containerId", direction = Sort.Direction.ASC) Pageable pageable) {
        String userId = authentication.getName();
        
        // [✅ 서비스 호출 시 Sort 객체 전달]
        List<ContainerStatusDto> containerStatuses = containerService.getContainerStatuses(userId, pageable.getSort());
        
        model.addAttribute("containers", containerStatuses);
        model.addAttribute("activeMenu", "container-inquiry");

        // [✅ 뷰에서 사용할 정렬 정보 추가]
        String currentSortField = pageable.getSort().iterator().next().getProperty();
        String currentSortDirection = pageable.getSort().iterator().next().getDirection().name();
        model.addAttribute("currentSortField", currentSortField);
        model.addAttribute("currentSortDirection", currentSortDirection); // [✅ 이 줄 추가]
        model.addAttribute("reverseSortDirection", currentSortDirection.equalsIgnoreCase("ASC") ? "desc" : "asc");

        return "fwd/FWD_container_inquiry";
    }

    @GetMapping("/transaction_history")
    public String fwdTransactionHistory(Model model) {
        model.addAttribute("activeMenu", "transaction_history");
        return "fwd/FWD_transaction_history";
    }
}