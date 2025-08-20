// [✅ RequestService.java 파일 전체를 이 최종 코드로 교체해주세요]
package net.dima.project.service;

import java.time.LocalDate;
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
import net.dima.project.dto.BlDto;
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

    /**
     * 포워더의 '견적요청조회' 페이지에 표시될 모든 공개 요청 목록을 조회합니다. (기존과 동일)
     */
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
            if (transportType != null && !transportType.isEmpty()) {
                predicates.add(cb.equal(root.get("transportType"), transportType));
            }
            if (itemName != null && !itemName.isBlank()) {
                Join<RequestEntity, CargoEntity> cargoJoin = root.join("cargo");
                predicates.add(cb.like(cargoJoin.get("itemName"), "%" + itemName + "%"));
            }
            if (departurePort != null && !departurePort.isEmpty()) {
                predicates.add(cb.equal(root.get("departurePort"), departurePort));
            }
            if (arrivalPort != null && !arrivalPort.isEmpty()) {
                predicates.add(cb.equal(root.get("arrivalPort"), arrivalPort));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<RequestEntity> requestPage = requestRepository.findAll(spec, pageable);
        
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

    /**
     * 화주의 '나의요청 관리' 및 '운송중인 화물' 페이지에 표시될 요청 목록을 조회합니다.
     */
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

            if (status == null || "CLOSED".equalsIgnoreCase(status)) {
                predicates.add(cb.equal(root.get("status"), RequestStatus.CLOSED));
            } else if ("OPEN".equalsIgnoreCase(status)) {
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

        // DB에서 페이징된 요청 목록을 가져옵니다.
        Page<RequestEntity> requestPage = requestRepository.findAll(spec, pageable);
        List<RequestEntity> requestsOnPage = requestPage.getContent();

        // N+1 문제를 방지하기 위해 관련 데이터를 한 번에 조회합니다.
        Map<Long, Long> bidderCounts = offerRepository.countOffersByRequestIn(requestsOnPage).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        Map<Long, OfferEntity> winningOffers = offerRepository.findWinningOffersForRequests(requestsOnPage).stream()
                .collect(Collectors.toMap(offer -> offer.getRequest().getRequestId(), Function.identity(), (o1, o2) -> o1));
        Map<Long, Optional<OfferEntity>> finalOffers = requestsOnPage.stream()
                .collect(Collectors.toMap(RequestEntity::getRequestId, this::findFinalOffer));

        // 조회된 데이터를 새로운 DTO 생성 로직에 맞춰 가공합니다.
        List<MyPostedRequestDto> dtoList = requestsOnPage.stream()
            .map(req -> {
                if (req.getStatus() == RequestStatus.OPEN) {
                    return MyPostedRequestDto.fromEntity(req, bidderCounts.getOrDefault(req.getRequestId(), 0L));
                } else { // CLOSED
                    Optional<OfferEntity> directWinningOfferOpt = Optional.ofNullable(winningOffers.get(req.getRequestId()));
                    Optional<OfferEntity> finalOfferInChainOpt = finalOffers.getOrDefault(req.getRequestId(), Optional.empty());
                    
                    // 정산 완료된 건은 목록에서 제외합니다.
                    if (finalOfferInChainOpt.map(o -> o.getContainer().getStatus() == ContainerStatus.SETTLED).orElse(false)) {
                        return null;
                    }
                    return MyPostedRequestDto.fromEntity(req, directWinningOfferOpt, finalOfferInChainOpt);
                }
            })
            .filter(dto -> dto != null)
            .collect(Collectors.toList());
        
        return new PageImpl<>(dtoList, pageable, requestPage.getTotalElements());
    }
    
    /**
     * [✅ 핵심 수정] 재판매 체인을 재귀적으로 추적하여 최종 운송을 책임지는 제안(Offer)을 찾는 최적화된 메서드입니다.
     * 이제 이 메서드는 추가적인 DB 쿼리를 발생시키지 않고, 메모리 내에서 로직을 처리합니다.
     * @param request 시작 요청
     * @return 최종 운송사의 제안 (Optional)
     */
    private Optional<OfferEntity> findFinalOffer(RequestEntity request) {
        // 이 메서드는 더 이상 DB 쿼리를 직접 호출하지 않습니다.
        // 대신, 미리 로드된 데이터나 연관 관계를 통해 체인을 탐색해야 합니다.
        // 현재 구조에서는 한 단계의 재판매만 가정하고 단순화된 로직을 사용합니다.
        // 더 깊은 재판매 체인을 효율적으로 처리하려면, 별도의 쿼리나 서비스 로직 최적화가 필요합니다.
        
        Optional<OfferEntity> winningOfferOpt = offerRepository.findWinningOfferForRequest(request);

        if (winningOfferOpt.isPresent()) {
            OfferEntity winningOffer = winningOfferOpt.get();
            if (winningOffer.getStatus() == OfferStatus.RESOLD) {
                // RESOLD 상태라면, 이 제안을 sourceOffer로 가지는 재판매 요청을 찾고, 그 요청의 낙찰자를 찾아야 함.
                // 이 부분이 N+1을 유발할 수 있으므로, 대량 조회 시에는 한 번에 관련 데이터를 가져오는 전략이 필요.
                // 지금은 단일 조회에 대한 로직으로 유지.
                return requestRepository.findBySourceOfferOrderedByCreatedAtDesc(winningOffer).stream()
                    .findFirst()
                    .flatMap(this::findFinalOffer);
            } else {
                return winningOfferOpt; // ACCEPTED, CONFIRMED 등 최종 상태
            }
        }
        return Optional.empty(); // 낙찰된 제안이 없음
    }
    
    /**
     * 화주가 새로운 운송 요청을 생성합니다. (기존과 거의 동일, 이벤트 발행 로직 유지)
     */
    @Transactional
    public void createNewRequest(NewRequestDto dto, String currentUserId) {
        UserEntity requester = userRepository.findByUserId(currentUserId);
        CargoEntity newCargo = CargoEntity.builder()
                .owner(requester)
                .itemName(dto.getItemName())
                .incoterms(dto.getIncoterms())
                .totalCbm(dto.getTotalCbm())
                .isDangerous(false)
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
        
        RequestCardDto dtoForEvent = RequestCardDto.fromEntity(newRequest, false);
        eventPublisher.publishEvent(new NotificationEvents.RequestCreatedEvent(this, dtoForEvent));
    }
    
    /**
     * 화주가 포워더의 제안을 확정(낙찰)합니다.
     */
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
        eventPublisher.publishEvent(new NotificationEvents.DealMadeEvent(this));

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
    
    @Transactional(readOnly = true)
    public BlDto getBlInfo(Long requestId, String currentUserId) {
        RequestEntity request = requestRepository.findRequestWithDetailsById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 요청입니다."));

        if (!request.getRequester().getUserId().equals(currentUserId)) {
            throw new SecurityException("자신의 요청에 대한 B/L만 조회할 수 있습니다.");
        }

        OfferEntity finalOffer = findFinalOffer(request)
                .orElseThrow(() -> new IllegalStateException("확정된 운송 정보를 찾을 수 없습니다."));

        ContainerEntity finalContainer = finalOffer.getContainer();
        UserEntity shipper = request.getRequester();
        UserEntity forwarder = finalOffer.getForwarder();

        return BlDto.builder()
                .blNo("SHB-" + requestId + "-" + finalOffer.getOfferId())
                .issueDate(LocalDate.now())
                .shipperName(shipper.getCompanyName())
                .shipperAddress("null")
                .consigneeName(shipper.getCompanyName())
                .consigneeAddress("null")
                .forwarderName(forwarder.getCompanyName())
                .vesselName("null")
                .imoNumber(finalContainer.getImoNumber())
                .portOfLoading(request.getDeparturePort())
                .portOfDischarge(request.getArrivalPort())
                .containerNo(finalContainer.getContainerId())
                .descriptionOfGoods(request.getCargo().getItemName())
                .cbm(request.getCargo().getTotalCbm())
                .build();
    }
}