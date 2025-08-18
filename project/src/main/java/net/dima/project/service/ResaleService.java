// [✅ /service/ResaleService.java 파일 전체를 이 최종 코드로 교체해주세요]
package net.dima.project.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import net.dima.project.dto.BidderDto;
import net.dima.project.dto.MyPostedRequestDto;
import net.dima.project.entity.*;
import net.dima.project.repository.ContainerCargoRepository;
import net.dima.project.repository.OfferRepository;
import net.dima.project.repository.RequestRepository;
import net.dima.project.repository.UserRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class ResaleService {

    private final OfferRepository offerRepository;
    private final RequestRepository requestRepository;
    private final UserRepository userRepository;
    private final ContainerCargoRepository containerCargoRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 특정 제안(Offer)을 재판매 시장에 내놓습니다. (기존과 동일)
     */
    @Transactional
    public void createResaleRequest(Long offerId, String currentUserId) {
        OfferEntity originalOffer = offerRepository.findByIdWithDetails(offerId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 제안입니다."));
        UserEntity forwarder = userRepository.findByUserId(currentUserId);

        if (originalOffer.getStatus() != OfferStatus.ACCEPTED) {
            throw new IllegalStateException("'수락(ACCEPTED)' 상태의 제안만 재판매할 수 있습니다.");
        }
        if (originalOffer.getContainer().getStatus() != ContainerStatus.SCHEDULED) {
            throw new IllegalStateException("컨테이너가 이미 확정 또는 운송 시작되어 재판매할 수 없습니다.");
        }
        if (!originalOffer.getForwarder().getUserSeq().equals(forwarder.getUserSeq())) {
            throw new SecurityException("자신의 제안만 재판매할 수 있습니다.");
        }

        originalOffer.setStatus(OfferStatus.FOR_SALE);

        RequestEntity resaleRequest = RequestEntity.builder()
                .cargo(originalOffer.getRequest().getCargo())
                .requester(forwarder)
                .departurePort(originalOffer.getRequest().getDeparturePort())
                .arrivalPort(originalOffer.getRequest().getArrivalPort())
                .deadline(originalOffer.getRequest().getDeadline())
                .desiredArrivalDate(originalOffer.getRequest().getDesiredArrivalDate())
                .tradeType(originalOffer.getRequest().getTradeType())
                .transportType(originalOffer.getRequest().getTransportType())
                .status(RequestStatus.OPEN)
                .sourceOffer(originalOffer)
                .build();
        requestRepository.save(resaleRequest);
    }

    /**
     * 재판매 요청을 취소하고 원래 상태로 되돌립니다. (기존과 동일)
     */
    @Transactional
    public void cancelResaleRequest(Long requestId, String currentUserId) {
        RequestEntity resaleRequest = requestRepository.findRequestWithDetailsById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 재판매 요청입니다: " + requestId));

        if (!resaleRequest.getRequester().getUserId().equals(currentUserId)) {
            throw new SecurityException("자신이 등록한 재판매 요청만 취소할 수 있습니다.");
        }
        if (resaleRequest.getStatus() != RequestStatus.OPEN) {
            throw new IllegalStateException("진행 중인 재판매 요청만 취소할 수 있습니다.");
        }

        revertResaleRequest(resaleRequest);
    }
    
    public void revertResaleRequest(RequestEntity resaleRequest) {
        OfferEntity originalOffer = resaleRequest.getSourceOffer();
        if (originalOffer == null) {
            throw new IllegalStateException("원본 제안이 없는 재판매 요청입니다.");
        }
        
        originalOffer.setStatus(OfferStatus.ACCEPTED);

        List<OfferEntity> bidsToCancel = offerRepository.findAllByRequest(resaleRequest);
        bidsToCancel.forEach(bid -> bid.setStatus(OfferStatus.REJECTED));

        resaleRequest.setStatus(RequestStatus.CLOSED);
    }
    
    /**
     * 포워더의 '재판매 관리' 페이지에 표시될 요청 목록을 조회합니다.
     */
    @Transactional(readOnly = true)
    public Page<MyPostedRequestDto> getMyPostedRequests(String currentUserId, String status, Pageable pageable) {
        UserEntity requester = userRepository.findByUserId(currentUserId);
        
        List<RequestEntity> allMyResaleRequests = requestRepository.findAllByRequesterAndSourceOfferIsNotNull(requester, pageable.getSort());

        List<RequestEntity> openRequests = allMyResaleRequests.stream().filter(r -> r.getStatus() == RequestStatus.OPEN).collect(Collectors.toList());
        List<RequestEntity> closedRequests = allMyResaleRequests.stream().filter(r -> r.getStatus() == RequestStatus.CLOSED).collect(Collectors.toList());

        Map<Long, Long> bidderCounts = offerRepository.countOffersByRequestIn(openRequests).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        Map<Long, OfferEntity> winningOffers = offerRepository.findWinningOffersForRequests(closedRequests).stream()
                .collect(Collectors.toMap(offer -> offer.getRequest().getRequestId(), Function.identity()));

        List<MyPostedRequestDto> dtoList = allMyResaleRequests.stream()
            .map(req -> {
                // 정산 완료된 건은 목록에서 제외
                Optional<OfferEntity> finalOfferOpt = findFinalOffer(req);
                if (finalOfferOpt.isPresent() && finalOfferOpt.get().getContainer().getStatus() == ContainerStatus.SETTLED) {
                    return null;
                }

                if (req.getStatus() == RequestStatus.OPEN) {
                    long bidderCount = bidderCounts.getOrDefault(req.getRequestId(), 0L);
                    return MyPostedRequestDto.fromEntity(req, bidderCount);
                } else { // CLOSED
                    Optional<OfferEntity> winningOfferOpt = Optional.ofNullable(winningOffers.get(req.getRequestId()));
                    if (winningOfferOpt.isEmpty()) {
                        return null; // 마감되었으나 낙찰자 없는 건(기간만료 자동취소)은 제외
                    }
                    return MyPostedRequestDto.fromEntity(req, winningOfferOpt);
                }
            })
            .filter(dto -> dto != null)
            .collect(Collectors.toList());

        // 상태(status) 탭 필터링 로직
        List<MyPostedRequestDto> filteredList;
        if (status != null && !status.isEmpty()) {
            filteredList = dtoList.stream()
                    .filter(dto -> {
                        if ("OPEN".equalsIgnoreCase(status)) {
                            return "OPEN".equals(dto.getStatus());
                        }
                        return dto.getDetailedStatus() != null && status.equalsIgnoreCase(dto.getDetailedStatus());
                    })
                    .collect(Collectors.toList());
        } else {
            filteredList = dtoList;
        }

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), filteredList.size());
        List<MyPostedRequestDto> pageContent = (start >= filteredList.size()) ? Collections.emptyList() : filteredList.subList(start, end);
        
        return new PageImpl<>(pageContent, pageable, filteredList.size());
    }
    
    /**
     * 특정 재판매 요청에 대한 입찰자 목록을 조회합니다. (기존과 동일)
     */
    @Transactional(readOnly = true)
    public List<BidderDto> getBiddersForRequest(Long requestId, String currentUserId) {
        RequestEntity request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 요청입니다."));
        if (!request.getRequester().getUserId().equals(currentUserId)) {
            throw new SecurityException("자신의 요청에 대한 입찰자만 조회할 수 있습니다.");
        }
        return offerRepository.findAllByRequest(request).stream()
                .map(BidderDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 재판매 요청에 대한 입찰을 확정(낙찰)합니다.
     */
    @Transactional
    public void confirmBid(Long requestId, Long winningOfferId, String currentUserId) {
        RequestEntity resaleRequest = requestRepository.findRequestWithDetailsById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 요청입니다."));
        if (!resaleRequest.getRequester().getUserId().equals(currentUserId)) {
            throw new SecurityException("자신의 요청에 대해서만 확정할 수 있습니다.");
        }
        if (resaleRequest.getStatus() != RequestStatus.OPEN) {
            throw new IllegalStateException("이미 마감된 요청입니다.");
        }

        List<OfferEntity> allBids = offerRepository.findAllByRequest(resaleRequest);
        OfferEntity winningOffer = allBids.stream()
                .filter(o -> o.getOfferId().equals(winningOfferId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("선택한 입찰이 존재하지 않습니다."));

        // [로직 간소화] 상태 변경 및 이벤트 발행만 수행
        allBids.forEach(bid -> bid.setStatus(bid.equals(winningOffer) ? OfferStatus.ACCEPTED : OfferStatus.REJECTED));
        resaleRequest.setStatus(RequestStatus.CLOSED);

        OfferEntity originalOffer = resaleRequest.getSourceOffer();
        originalOffer.setStatus(OfferStatus.RESOLD);
        
        eventPublisher.publishEvent(new NotificationEvents.OfferConfirmedEvent(this, allBids, winningOffer));
        eventPublisher.publishEvent(new NotificationEvents.DealMadeEvent(this));
        
        // 기존 화물-컨테이너 연결 정보 삭제
        containerCargoRepository.findByOfferOfferId(originalOffer.getOfferId())
                .ifPresent(containerCargoRepository::delete);
        
        // 새로운 낙찰 정보로 화물-컨테이너 연결 정보 생성
        containerCargoRepository.findByOfferOfferId(winningOffer.getOfferId()).orElseGet(() -> {
            ContainerCargoEntity newCargo = ContainerCargoEntity.builder()
                    .container(winningOffer.getContainer())
                    .offer(winningOffer)
                    .cbmLoaded(resaleRequest.getCargo().getTotalCbm())
                    .isExternal(false)
                    .freightCost(winningOffer.getPrice())
                    .freightCurrency(winningOffer.getCurrency())
                    .build();
            return containerCargoRepository.save(newCargo);
        });
    }
    
    /**
     * 재판매 체인을 추적하여 최종 운송 제안을 찾습니다. (RequestService의 것과 동일한 로직)
     */
    private Optional<OfferEntity> findFinalOffer(RequestEntity request) {
        RequestEntity currentRequest = request;
        while (true) {
            Optional<OfferEntity> winningOfferOpt = offerRepository.findWinningOfferForRequest(currentRequest);

            if (winningOfferOpt.isPresent()) {
                OfferEntity winningOffer = winningOfferOpt.get();
                if (winningOffer.getStatus() == OfferStatus.RESOLD) {
                    List<RequestEntity> nextRequests = requestRepository.findBySourceOfferOrderedByCreatedAtDesc(winningOffer);
                    if (!nextRequests.isEmpty()) {
                        currentRequest = nextRequests.get(0);
                    } else {
                        return winningOfferOpt;
                    }
                } else {
                    return winningOfferOpt;
                }
            } else {
                return Optional.empty();
            }
        }
    }
}