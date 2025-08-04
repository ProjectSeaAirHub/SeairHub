package net.dima.project.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Subquery;

import org.springframework.data.domain.PageImpl; // [✅ import 추가]


import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import net.dima.project.dto.BidderDto;         // import 추가
import net.dima.project.dto.MyPostedRequestDto; // import 추가
import net.dima.project.entity.ContainerCargoEntity;
import net.dima.project.entity.ContainerStatus;
import net.dima.project.entity.OfferEntity;
import net.dima.project.entity.OfferStatus;
import net.dima.project.entity.RequestEntity;
import net.dima.project.entity.RequestStatus;
import net.dima.project.entity.UserEntity;
import net.dima.project.repository.ContainerCargoRepository; // import 추가
import net.dima.project.repository.OfferRepository;
import net.dima.project.repository.RequestRepository;
import net.dima.project.repository.UserRepository;

import java.util.ArrayList; // [✅ import 추가]
import jakarta.persistence.criteria.Predicate; // [✅ import 추가]
import jakarta.persistence.criteria.Root; // [✅ import 추가]

@Service
@RequiredArgsConstructor
@Transactional
public class ResaleService {

    private final OfferRepository offerRepository;
    private final RequestRepository requestRepository;
    private final UserRepository userRepository;
    private final ContainerCargoRepository containerCargoRepository;
    

    @Transactional
    public void createResaleRequest(Long offerId, String currentUserId) {
        OfferEntity originalOffer = offerRepository.findByIdWithDetails(offerId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 제안입니다."));
        UserEntity forwarder = userRepository.findByUserId(currentUserId);

        // [✅ 수정] 재판매는 '수락(ACCEPTED)' 상태일 때만 가능
        if (originalOffer.getStatus() != OfferStatus.ACCEPTED) {
            throw new IllegalStateException("'수락(ACCEPTED)' 상태의 제안만 재판매할 수 있습니다.");
        }
        // [✅ 수정] 컨테이너가 확정되기 전, 즉 'SCHEDULED' 상태일 때만 가능
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
                .tradeType(originalOffer.getRequest().getTradeType())
                .transportType(originalOffer.getRequest().getTransportType())
                // [✅ 핵심 수정] 원본 요청의 희망 도착일을 그대로 복사합니다.
                .desiredArrivalDate(originalOffer.getRequest().getDesiredArrivalDate())
                .status(RequestStatus.OPEN)
                .sourceOffer(originalOffer)
                .build();
        requestRepository.save(resaleRequest);
    }
    @Transactional
    public void cancelResaleRequest(Long requestId, String currentUserId) {
        RequestEntity resaleRequest = requestRepository.findRequestWithDetailsById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 요청입니다: " + requestId));
        UserEntity forwarder = userRepository.findByUserId(currentUserId);

        if (!resaleRequest.getRequester().getUserSeq().equals(forwarder.getUserSeq())) {
            throw new SecurityException("자신이 등록한 재판매 요청만 취소할 수 있습니다.");
        }
        OfferEntity originalOffer = resaleRequest.getSourceOffer();
        if (originalOffer == null) {
            throw new IllegalStateException("이 요청은 재판매 요청이 아니므로 취소할 수 없습니다.");
        }

        // 1. 나의 원본 제안 상태를 'ACCEPTED'(수락)로 되돌립니다.
        originalOffer.setStatus(OfferStatus.ACCEPTED);
        
        // 2. 이 재판매 요청에 달린 모든 입찰(Offer)들의 상태를 'REJECTED'(거절)로 변경합니다.
        List<OfferEntity> bidsToCancel = offerRepository.findAllByRequest(resaleRequest);
        for (OfferEntity bid : bidsToCancel) {
            bid.setStatus(OfferStatus.REJECTED);
        }

        // 3. [✅ 핵심 수정] 재판매 요청을 삭제하는 대신 상태를 'CLOSED'로 변경합니다.
        resaleRequest.setStatus(RequestStatus.CLOSED);
        
        // ※ 참고: @Transactional 환경이므로, 변경된 모든 엔티티(originalOffer, bidsToCancel, resaleRequest)는
        // 메서드가 끝날 때 자동으로 DB에 저장(UPDATE)됩니다.
    }
    
    
    // [✅ 추가] 나의 재판매 요청 목록 조회
    // [✅ 이 메서드 전체를 아래 코드로 교체해주세요]
 // [✅ 기존 getMyPostedRequests 메서드를 아래의 새로운 코드로 전체 교체해주세요]

    @Transactional(readOnly = true)
    public Page<MyPostedRequestDto> getMyPostedRequests(String currentUserId, String status, Pageable pageable) {
        UserEntity requester = userRepository.findByUserId(currentUserId);

        // 1. DB에서는 정렬만 적용하여 나의 모든 재판매 요청을 일단 다 가져옵니다.
        List<RequestEntity> allMyRequests = requestRepository.findAllByRequesterAndSourceOfferIsNotNull(requester, pageable.getSort());

        // 2. 가져온 데이터를 DTO로 변환합니다.
        List<MyPostedRequestDto> dtoList = allMyRequests.stream().map(req -> {
            if (req.getStatus() == RequestStatus.OPEN) {
                long bidderCount = offerRepository.countByRequest(req);
                return MyPostedRequestDto.fromEntity(req, bidderCount);
            } else {
                Optional<OfferEntity> winningOfferOpt = offerRepository.findAllByRequest(req)
                        .stream()
                        .filter(o -> o.getStatus() != OfferStatus.PENDING && o.getStatus() != OfferStatus.REJECTED)
                        .findFirst();
                return MyPostedRequestDto.fromEntity(req, winningOfferOpt);
            }
        }).collect(Collectors.toList());

        // 3. 상태(status) 필터가 있다면, 자바 스트림을 사용해 직접 필터링합니다.
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
            filteredList = dtoList; // 필터가 없으면 전체 목록 사용
        }

        // 4. 필터링된 최종 목록을 가지고 수동으로 페이지네이션 객체를 만듭니다.
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), filteredList.size());
        List<MyPostedRequestDto> pageContent = (start > end) ? List.of() : filteredList.subList(start, end);
        
        return new PageImpl<>(pageContent, pageable, filteredList.size());
    }

    // [✅ 추가] 특정 재판매 요청에 대한 입찰자 목록 조회
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

    // [✅ 추가] 입찰 확정 (가장 핵심적인 로직)
    @Transactional
    public void confirmBid(Long requestId, Long winningOfferId, String currentUserId) {
        // 1~4단계: 상태 변경 (기존 로직과 동일)
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

        // [✅ 5. 핵심 수정] 나의 컨테이너에서 화물(CBM) 제거 로직 보강
        // findByOfferOfferId로 데이터를 찾되, 만약 없더라도 오류를 발생시키지 않고 넘어갑니다.=
        // [✅ 수정] 나의 컨테이너에서 화물(CBM) 제거 로직 보강
        containerCargoRepository.findByOfferOfferId(originalOffer.getOfferId())
                .ifPresent(containerCargoRepository::delete);
        
        // [✅ 6. 핵심 수정] 낙찰자 컨테이너에 화물(CBM) 추가 로직 강화
        // 혹시 모를 중복 생성을 방지하기 위해, 먼저 데이터가 있는지 확인합니다.
        // [✅ 수정] 낙찰자 컨테이너에 화물(CBM) 추가 로직 강화
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
}