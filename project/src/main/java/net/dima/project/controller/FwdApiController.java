package net.dima.project.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dima.project.dto.*;
import net.dima.project.service.ContainerService;
import net.dima.project.service.OfferService;
import net.dima.project.service.ResaleService;
import net.dima.project.service.TransactionHistoryService;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/fwd")
@RequiredArgsConstructor
@Slf4j
public class FwdApiController {

    // [✅ 수정] 기능에 맞는 서비스들을 주입
    private final ContainerService containerService;
    private final OfferService offerService;
    private final ResaleService resaleService;
    private final TransactionHistoryService transactionHistoryService; // [✅ 추가]

    @GetMapping("/available-containers")
    public ResponseEntity<List<AvailableContainerDto>> getAvailableContainers(@RequestParam("requestId") Long requestId, Authentication authentication) {
        String userId = authentication.getName();
        List<AvailableContainerDto> containers = containerService.getAvailableContainers(requestId, userId);
        return ResponseEntity.ok(containers);
    }

    @PostMapping("/offers")
    public ResponseEntity<String> submitOffer(@RequestBody OfferRequestDto offerDto, Authentication authentication) {
        try {
            String userId = authentication.getName();
            offerService.createOffer(offerDto, userId);
            return ResponseEntity.ok("제안이 성공적으로 등록되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/external-cargo")
    public ResponseEntity<String> addExternalCargo(@RequestBody ExternalCargoDto dto, Authentication authentication) {
        try {
            String userId = authentication.getName();
            containerService.addExternalCargo(dto, userId);
            return ResponseEntity.ok("외부 화물이 성공적으로 등록되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/external-cargo/{cargoId}")
    public ResponseEntity<String> deleteExternalCargo(@PathVariable("cargoId") Long cargoId, Authentication authentication) {
        try {
            String userId = authentication.getName();
            containerService.deleteExternalCargo(cargoId, userId);
            return ResponseEntity.ok("외부 화물이 성공적으로 삭제되었습니다.");
        } catch (Exception e) {
            log.error("Failed to delete external cargo: {}", cargoId, e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    
    @PostMapping("/resale/{offerId}")
    public ResponseEntity<String> createResale(@PathVariable("offerId") Long offerId, Authentication authentication) {
        try {
            String userId = authentication.getName();
            resaleService.createResaleRequest(offerId, userId);
            return ResponseEntity.ok("재판매 요청이 등록되었습니다. 견적요청조회 페이지에서 확인하세요.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/resale-request/{requestId}")
    public ResponseEntity<String> cancelResale(@PathVariable("requestId") Long requestId, Authentication authentication) {
        try {
            String userId = authentication.getName();
            resaleService.cancelResaleRequest(requestId, userId);
            return ResponseEntity.ok("재판매 요청이 취소되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/details/{containerId}/{status}")
    public ResponseEntity<List<CargoDetailDto>> getCargoDetails(
            @PathVariable("containerId") String containerId,
            @PathVariable("status") String status,
            Authentication authentication) {
        try {
            String userId = authentication.getName();
            List<CargoDetailDto> details = containerService.getDetailsForContainerStatus(containerId, status, userId);
            return ResponseEntity.ok(details);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    @GetMapping("/my-offers/{offerId}")
    public ResponseEntity<MyOfferDetailDto> getMyOfferDetails(@PathVariable("offerId") Long offerId, Authentication authentication) {
        try {
            String currentUserId = authentication.getName();
            MyOfferDetailDto details = offerService.getMyOfferDetails(offerId, currentUserId);
            return ResponseEntity.ok(details);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(null);
        }
    }
    
    @DeleteMapping("/my-offers/{offerId}")
    public ResponseEntity<String> cancelOffer(@PathVariable("offerId") Long offerId, Authentication authentication) {
        try {
            String userId = authentication.getName();
            offerService.cancelOffer(offerId, userId);
            return ResponseEntity.ok("제안이 성공적으로 취소되었습니다.");
        } catch (Exception e) {
            log.error("Failed to cancel offer: {}", offerId, e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    
    
    // [✅ 추가] 제안 운임 수정을 위한 PATCH API
    @PatchMapping("/my-offers/{offerId}")
    public ResponseEntity<String> updateOfferPrice(
            @PathVariable("offerId") Long offerId,
            @RequestBody UpdateOfferDto dto,
            Authentication authentication) {
        try {
            String userId = authentication.getName();
            offerService.updateOfferPrice(offerId, dto, userId);
            return ResponseEntity.ok("제안 가격이 성공적으로 수정되었습니다.");
        } catch (Exception e) {
            log.error("Failed to update offer price for offerId: {}", offerId, e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    
  

    // [✅ 추가] 특정 재판매 요청의 입찰자 목록 조회 API
    // [✅ 이 메서드를 수정합니다]
    @GetMapping("/my-posted-requests/{requestId}/bidders")
    public ResponseEntity<List<BidderDto>> getBidders(
            @PathVariable("requestId") Long requestId,
            Authentication authentication) {
        try {
            String userId = authentication.getName();
            List<BidderDto> bidders = resaleService.getBiddersForRequest(requestId, userId);
            return ResponseEntity.ok(bidders);
        } catch (Exception e) {
            log.error("Failed to get bidders for request: {}", requestId, e);
            return ResponseEntity.badRequest().body(null);
        }
    }


    // [✅ 추가] 입찰 확정 API
    // [✅ 이 메서드를 수정합니다]
    @PostMapping("/my-posted-requests/{requestId}/confirm")
    public ResponseEntity<String> confirmBid(
            @PathVariable("requestId") Long requestId,
            @RequestBody Map<String, Long> payload, 
            Authentication authentication) {
        try {
            String userId = authentication.getName();
            Long winningOfferId = payload.get("offerId");
            if (winningOfferId == null) {
                return ResponseEntity.badRequest().body("winningOfferId가 필요합니다.");
            }
            resaleService.confirmBid(requestId, winningOfferId, userId);
            return ResponseEntity.ok("성공적으로 낙찰 처리되었습니다.");
        } catch (Exception e) {
            log.error("Failed to confirm bid for request: {}", requestId, e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    
    
    // [✅ 추가] 컨테이너 생성을 위한 POST API
    @PostMapping("/containers")
    public ResponseEntity<String> createContainer(@RequestBody CreateContainerDto dto, Authentication authentication) {
        try {
            String userId = authentication.getName();
            containerService.createContainer(dto, userId);
            return ResponseEntity.ok("컨테이너가 성공적으로 등록되었습니다.");
        } catch (Exception e) {
            log.error("Failed to create container", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    
    // [✅ 추가] 컨테이너 삭제 API
    // [✅ 이 메서드를 수정합니다]
    @DeleteMapping("/containers/{containerId}")
    public ResponseEntity<String> deleteContainer(
            @PathVariable("containerId") String containerId, // [✅ 수정]
            Authentication authentication) {
        try {
            String userId = authentication.getName();
            containerService.deleteContainer(containerId, userId);
            return ResponseEntity.ok("컨테이너가 삭제되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    // [✅ 추가] 컨테이너 확정 API
    // [✅ 이 메서드를 수정합니다]
    @PostMapping("/containers/{containerId}/confirm")
    public ResponseEntity<String> confirmContainer(
            @PathVariable("containerId") String containerId, // [✅ 수정]
            Authentication authentication) {
        try {
            String userId = authentication.getName();
            containerService.confirmContainer(containerId, userId);
            return ResponseEntity.ok("컨테이너가 확정 처리되었습니다. 이제 수정 및 삭제가 불가능합니다.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    
    
    
    // [✅ 추가] 선적완료 API
    @PostMapping("/containers/{containerId}/ship")
    public ResponseEntity<String> shipContainer(@PathVariable("containerId") String containerId, Authentication authentication) {
        try {
            containerService.shipContainer(containerId, authentication.getName());
            return ResponseEntity.ok("컨테이너가 '선적완료' 처리되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // [✅ 추가] 운송완료 API
    @PostMapping("/containers/{containerId}/complete")
    public ResponseEntity<String> completeShipment(@PathVariable("containerId") String containerId, Authentication authentication) {
        try {
            containerService.completeShipment(containerId, authentication.getName());
            return ResponseEntity.ok("컨테이너가 '운송완료' 처리되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    
    
    // [✅ 추가] 거래내역 조회 API 3종
    @GetMapping("/transactions")
    public ResponseEntity<List<TransactionHistoryDto>> getTransactions(
            Authentication authentication,
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(name = "keyword", required = false) String keyword) {
        return ResponseEntity.ok(transactionHistoryService.getTransactionHistory(authentication.getName(), startDate, endDate, keyword));
    }

    @GetMapping("/transactions/sales")
    public ResponseEntity<List<TransactionHistoryDto>> getSales(
            Authentication authentication,
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(name = "keyword", required = false) String keyword) {
        return ResponseEntity.ok(transactionHistoryService.getSalesHistory(authentication.getName(), startDate, endDate, keyword));
    }

    @GetMapping("/transactions/purchases")
    public ResponseEntity<List<TransactionHistoryDto>> getPurchases(
            Authentication authentication,
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(name = "keyword", required = false) String keyword) {
        return ResponseEntity.ok(transactionHistoryService.getPurchaseHistory(authentication.getName(), startDate, endDate, keyword));
    }
    
    @PostMapping("/containers/{containerId}/settle")
    public ResponseEntity<String> settleContainer(@PathVariable("containerId") String containerId, Authentication authentication) {
        try {
            containerService.settleContainer(containerId, authentication.getName());
            return ResponseEntity.ok("컨테이너가 '정산완료' 처리되었습니다. 거래내역 탭에서 확인하세요.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    
}