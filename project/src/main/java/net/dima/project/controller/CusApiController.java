package net.dima.project.controller;

import lombok.RequiredArgsConstructor;
import net.dima.project.dto.NewRequestDto;
import net.dima.project.service.RequestService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import net.dima.project.service.OfferService; // import 추가
import net.dima.project.dto.OfferDto; // import 추가
import java.util.List; // import 추가
import java.util.Map;   // import 추가

@RestController
@RequestMapping("/api/cus")
@RequiredArgsConstructor
public class CusApiController {

    private final RequestService requestService;
    private final OfferService offerService;

    @PostMapping("/requests")
    public ResponseEntity<String> createRequest(@RequestBody NewRequestDto dto, Authentication authentication) {
        try {
            String userId = authentication.getName();
            requestService.createNewRequest(dto, userId);
            return ResponseEntity.ok("화물 요청이 성공적으로 등록되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("요청 등록 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
    
    
    @GetMapping("/requests/{requestId}/offers")
    public ResponseEntity<List<OfferDto>> getOffers(@PathVariable("requestId") Long requestId, Authentication authentication) {
        try {
            String userId = authentication.getName();
            List<OfferDto> offers = offerService.getOffersForShipperRequest(requestId, userId);
            return ResponseEntity.ok(offers);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    @PostMapping("/requests/{requestId}/confirm")
    public ResponseEntity<String> confirmOffer(@PathVariable("requestId") Long requestId,
                                                 @RequestBody Map<String, Long> payload,
                                                 Authentication authentication) {
        try {
            String userId = authentication.getName();
            Long winningOfferId = payload.get("winningOfferId");
            requestService.confirmShipperOffer(requestId, winningOfferId, userId);
            return ResponseEntity.ok("성공적으로 확정되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}