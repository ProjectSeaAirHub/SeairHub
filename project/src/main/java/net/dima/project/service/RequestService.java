// [✅ RequestService.java 파일 전체를 이 최종 코드로 교체해주세요]
package net.dima.project.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import net.dima.project.dto.MyPostedRequestDto;
import net.dima.project.dto.NewRequestDto;
import net.dima.project.dto.RequestCardDto;
import net.dima.project.entity.*;
import net.dima.project.repository.CargoRepository;
import net.dima.project.repository.ContainerCargoRepository;
import net.dima.project.repository.OfferRepository;
import net.dima.project.repository.RequestRepository;
import net.dima.project.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RequestService {

    private final RequestRepository requestRepository;
    private final OfferRepository offerRepository;
    private final UserRepository userRepository;
    private final CargoRepository cargoRepository;
    private final ContainerCargoRepository containerCargoRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ChatService chatService;

    // (getRequests, createNewRequest, confirmShipperOffer, findFinalOffer 메소드는 이전과 동일)
    public Page<RequestCardDto> getRequests(
            boolean excludeClosed,
            String tradeType, String transportType,
            String departurePort, String arrivalPort,
            String itemName, Pageable pageable, String currentUserId) {

        Specification<RequestEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();
            if (excludeClosed) {
                predicates.add(cb.equal(root.get("status"), RequestStatus.OPEN));
                predicates.add(cb.greaterThan(root.get("deadline"), now));
            }
            if (tradeType != null && !tradeType.isEmpty()) {
                predicates.add(cb.equal(root.get("tradeType"), tradeType));
            }
            // ... (다른 필터 조건들)
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<RequestEntity> requestPage = requestRepository.findAll(spec, pageable);
        
        // 🚀 성능 개선: 현재 페이지에 표시될 요청들에 대해서만 내가 제안했는지 확인
        List<RequestEntity> requestsOnPage = requestPage.getContent();
        if (requestsOnPage.isEmpty()) {
            return requestPage.map(req -> RequestCardDto.fromEntity(req, false));
        }

        Set<Long> offeredRequestIds = offerRepository.findOfferedRequestIdsByUserIdAndRequestIn(currentUserId, requestsOnPage);
        
        return requestPage.map(entity -> {
            boolean hasMyOffer = offeredRequestIds.contains(entity.getRequestId());
            return RequestCardDto.fromEntity(entity, hasMyOffer);
        });
    }

    @Transactional(readOnly = true)
    public Page<MyPostedRequestDto> getRequestsForShipper(String currentUserId, String status, boolean excludeClosed, String itemName, Pageable pageable) {
        UserEntity shipper = userRepository.findByUserId(currentUserId);
        LocalDateTime now = LocalDateTime.now();

        Specification<RequestEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("requester"), shipper));
            predicates.add(cb.isNull(root.get("sourceOffer")));

            if (itemName != null && !itemName.isBlank()) {
                Join<RequestEntity, CargoEntity> cargoJoin = root.join("cargo");
                predicates.add(cb.like(cargoJoin.get("itemName"), "%" + itemName + "%"));
            }

            // '운송중인 화물' 탭 (status="CLOSED" 또는 status=null)
            if (status == null || "CLOSED".equalsIgnoreCase(status)) {
                predicates.add(cb.equal(root.get("status"), RequestStatus.CLOSED));
            }
            // '나의요청 관리' 탭 (status="OPEN")
            else if ("OPEN".equalsIgnoreCase(status)) {
                predicates.add(cb.equal(root.get("status"), RequestStatus.OPEN));
                if (excludeClosed) {
                    predicates.add(cb.greaterThan(root.get("deadline"), now));
                }
            }
            
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("cargo", JoinType.LEFT);
            }
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<RequestEntity> requestPage = requestRepository.findAll(spec, pageable);
        List<RequestEntity> requestsOnPage = requestPage.getContent();

        List<RequestEntity> openRequests = requestsOnPage.stream().filter(r -> r.getStatus() == RequestStatus.OPEN).collect(Collectors.toList());
        List<RequestEntity> closedRequests = requestsOnPage.stream().filter(r -> r.getStatus() == RequestStatus.CLOSED).collect(Collectors.toList());

        Map<Long, Long> bidderCounts = offerRepository.countOffersByRequestIn(openRequests).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        Map<Long, OfferEntity> winningOffers = offerRepository.findWinningOffersForRequests(closedRequests).stream()
                .collect(Collectors.toMap(offer -> offer.getRequest().getRequestId(), Function.identity(), (o1, o2) -> o1));
        Map<Long, Optional<OfferEntity>> finalOffers = closedRequests.stream()
                .collect(Collectors.toMap(RequestEntity::getRequestId, this::findFinalOffer));

        // ✅ Page의 내용물(List)을 스트림으로 변환하여 처리 후, 새로운 Page 객체로 다시 만듭니다.
        List<MyPostedRequestDto> dtoList = requestsOnPage.stream()
            .map(req -> {
                if (req.getStatus() == RequestStatus.OPEN) {
                    return MyPostedRequestDto.fromEntity(req, bidderCounts.getOrDefault(req.getRequestId(), 0L));
                } else { // CLOSED
                    Optional<OfferEntity> directWinningOfferOpt = Optional.ofNullable(winningOffers.get(req.getRequestId()));
                    Optional<OfferEntity> finalOfferInChainOpt = finalOffers.getOrDefault(req.getRequestId(), Optional.empty());

                    if (finalOfferInChainOpt.map(o -> o.getContainer().getStatus() == ContainerStatus.SETTLED).orElse(false)) {
                        return null; 
                    }
                    return MyPostedRequestDto.fromEntity(req, directWinningOfferOpt, finalOfferInChainOpt);
                }
            })
            .filter(dto -> dto != null) // 정산완료 건(null)을 최종적으로 걸러냅니다.
            .collect(Collectors.toList());
            
        // ✅ PageImpl을 사용하여 필터링된 리스트와 기존 페이징 정보를 합쳐 새로운 Page 객체를 생성하여 반환합니다.
        return new PageImpl<>(dtoList, pageable, requestPage.getTotalElements());
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
    
    @Transactional
    public void createNewRequest(NewRequestDto dto, String currentUserId) {
        UserEntity requester = userRepository.findByUserId(currentUserId);
        CargoEntity newCargo = CargoEntity.builder()
                .owner(requester)
                .itemName(dto.getItemName())
                .incoterms(dto.getIncoterms())
                .totalCbm(dto.getTotalCbm())
                .isDangerous(dto.getIsDangerous())
                .build();
        cargoRepository.save(newCargo);

        RequestEntity newRequest = RequestEntity.builder()
                .cargo(newCargo)
                .requester(requester)
                .departurePort(dto.getDeparturePort())
                .arrivalPort(dto.getArrivalPort())
                .deadline(dto.getDeadline())
                .desiredArrivalDate(dto.getDesiredArrivalDate()) 
                .tradeType(dto.getTradeType())
                .transportType(dto.getTransportType())
                .status(RequestStatus.OPEN)
                .sourceOffer(null)
                .build();
        requestRepository.save(newRequest);
    }
    
    @Transactional
    public void confirmShipperOffer(Long requestId, Long winningOfferId, String currentUserId) {
        RequestEntity request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 요청입니다."));

        if (!request.getRequester().getUserId().equals(currentUserId)) {
            throw new SecurityException("자신의 요청에 대해서만 확정할 수 있습니다.");
        }
        if (request.getStatus() != RequestStatus.OPEN) {
            throw new IllegalStateException("이미 마감된 요청입니다.");
        }

        List<OfferEntity> allOffers = offerRepository.findAllByRequest(request);
        OfferEntity winningOffer = allOffers.stream()
                .filter(o -> o.getOfferId().equals(winningOfferId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 제안입니다."));
        
        allOffers.forEach(offer -> {
            offer.setStatus(offer.equals(winningOffer) ? OfferStatus.ACCEPTED : OfferStatus.REJECTED);
        });
        request.setStatus(RequestStatus.CLOSED);
        
        eventPublisher.publishEvent(new NotificationEvents.OfferConfirmedEvent(this, allOffers, winningOffer));

        if (!containerCargoRepository.existsByOffer_OfferId(winningOfferId)) {
            ContainerCargoEntity cargoInContainer = ContainerCargoEntity.builder()
                    .container(winningOffer.getContainer())
                    .offer(winningOffer)
                    .cbmLoaded(request.getCargo().getTotalCbm())
                    .isExternal(false)
                    .freightCost(winningOffer.getPrice())
                    .freightCurrency(winningOffer.getCurrency())
                    .build();
            containerCargoRepository.save(cargoInContainer);
        }
    }
}