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
    private final ChatService chatService;

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
        Map<Long, Optional<OfferEntity>> finalOffers = closedRequests.stream()
                .collect(Collectors.toMap(RequestEntity::getRequestId, this::findFinalOffer));

        List<MyPostedRequestDto> dtoList = allMyResaleRequests.stream()
            .map(req -> {
                Optional<OfferEntity> finalOfferOpt = finalOffers.get(req.getRequestId());
                if (finalOfferOpt != null && finalOfferOpt.isPresent() && finalOfferOpt.get().getContainer().getStatus() == ContainerStatus.SETTLED) {
                    return null;
                }

                if (req.getStatus() == RequestStatus.OPEN) {
                    long bidderCount = bidderCounts.getOrDefault(req.getRequestId(), 0L);
                    return MyPostedRequestDto.fromEntity(req, bidderCount);
                } else { // CLOSED
                    Optional<OfferEntity> winningOfferOpt = Optional.ofNullable(winningOffers.get(req.getRequestId()));
                    
                    // [✅ 핵심 수정] 마감된 요청인데 낙찰자가 없으면(즉, 기간만료로 자동취소된 건이면) 목록에서 제외합니다.
                    if (winningOfferOpt.isEmpty()) {
                        return null;
                    }
                    
                    MyPostedRequestDto dto = MyPostedRequestDto.fromEntity(req, winningOfferOpt);
                    finalOfferOpt.ifPresent(finalOffer -> dto.setImoNumber(finalOffer.getContainer().getImoNumber()));
                    return dto;
                }
            })
            .filter(dto -> dto != null) // null로 반환된 항목(기간만료, 정산완료 건)을 최종적으로 걸러냅니다.
            .collect(Collectors.toList());

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

        allBids.forEach(bid -> bid.setStatus(bid.equals(winningOffer) ? OfferStatus.ACCEPTED : OfferStatus.REJECTED));
        resaleRequest.setStatus(RequestStatus.CLOSED);

        OfferEntity originalOffer = resaleRequest.getSourceOffer();
        originalOffer.setStatus(OfferStatus.RESOLD);
        
        eventPublisher.publishEvent(new NotificationEvents.OfferConfirmedEvent(this, allBids, winningOffer));
        
        containerCargoRepository.findByOfferOfferId(originalOffer.getOfferId())
                .ifPresent(containerCargoRepository::delete);
        
        containerCargoRepository.findByOfferOfferId(winningOffer.getOfferId()).orElseGet(() -> {
            ContainerCargoEntity newCargo = ContainerCargoEntity.builder()
                    .container(winningOffer.getContainer())
                    .offer(winningOffer)
                    .cbmLoaded(resaleRequest.getCargo().getTotalCbm())
                    .isExternal(false)
                    .build();
            return containerCargoRepository.save(newCargo);
        });
    }
    
    private Optional<OfferEntity> findFinalOffer(RequestEntity request) {
        RequestEntity currentRequest = request;
        while (true) {
            Optional<OfferEntity> winningOfferOpt = offerRepository.findAllByRequest(currentRequest)
                    .stream()
                    .filter(o -> o.getStatus() != OfferStatus.PENDING && o.getStatus() != OfferStatus.REJECTED)
                    .findFirst();

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