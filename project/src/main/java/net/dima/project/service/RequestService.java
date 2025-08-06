// [✅ RequestService.java 파일 전체를 이 코드로 교체해주세요]
package net.dima.project.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Join;
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
            if (departurePort != null && !departurePort.isEmpty()) {
                predicates.add(cb.equal(root.get("departurePort"), departurePort));
            }
            if (arrivalPort != null && !arrivalPort.isEmpty()) {
                predicates.add(cb.equal(root.get("arrivalPort"), arrivalPort));
            }
            if (itemName != null && !itemName.isBlank()) {
                Join<RequestEntity, CargoEntity> cargoJoin = root.join("cargo");
                predicates.add(cb.like(cargoJoin.get("itemName"), "%" + itemName + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<RequestEntity> requestPage = requestRepository.findAll(spec, pageable);
        Set<Long> offeredRequestIds = offerRepository.findOfferedRequestIdsByUserId(currentUserId);
        return requestPage.map(entity -> {
            boolean hasMyOffer = offeredRequestIds.contains(entity.getRequestId());
            return RequestCardDto.fromEntity(entity, hasMyOffer);
        });
    }

    // ★★★ 핵심: 중복 메서드를 하나로 합치고, excludeClosed 파라미터를 추가했습니다 ★★★
    @Transactional(readOnly = true)
    public Page<MyPostedRequestDto> getRequestsForShipper(String currentUserId, String status, boolean excludeClosed, String itemName, Pageable pageable) {
        UserEntity shipper = userRepository.findByUserId(currentUserId);
        
        // 1. DB에서 나의 모든 원본 요청을 가져옵니다.
        List<RequestEntity> allMyRequests = requestRepository.findByRequesterAndSourceOfferIsNull(shipper, pageable.getSort());

        LocalDateTime now = LocalDateTime.now();

        // [✅ 2. 핵심 추가] 마감 제안 제외 필터링 로직
        // DTO로 변환하기 전에 원본 Entity 리스트를 먼저 필터링합니다.
        // [✅ 핵심 수정] 정산 완료(SETTLED)된 요청을 제외하는 필터링 로직 추가
        List<RequestEntity> filteredRequests = allMyRequests.stream()
                .filter(req -> {
                    // 요청이 'CLOSED' 상태일 때만 정산 완료 여부를 확인
                    if (req.getStatus() == RequestStatus.CLOSED) {
                        // 최종 연결된 컨테이너의 상태가 SETTLED가 아닌 경우에만 목록에 포함
                        return findFinalOffer(req)
                                .map(offer -> offer.getContainer().getStatus() != ContainerStatus.SETTLED)
                                .orElse(true); // 최종 제안을 찾을 수 없는 경우(예외 상황)에는 일단 포함
                    }
                    // 'OPEN' 상태인 요청은 항상 포함
                    return true;
                })
                // 기존의 '마감 제안 제외' 필터링 로직은 그대로 유지
                .filter(req -> 
                    !excludeClosed ||
                    (req.getStatus() == RequestStatus.OPEN && req.getDeadline().isAfter(now)) ||
                    (req.getStatus() == RequestStatus.CLOSED)
                )
                .collect(Collectors.toList());
        


        // 3. 필터링된 요청들을 DTO로 변환합니다.
        List<MyPostedRequestDto> dtoList = filteredRequests.stream().map(req -> {
            // ... (기존 DTO 변환 로직은 동일) ...
            if (req.getStatus() == RequestStatus.OPEN) {
                long bidderCount = offerRepository.countByRequest(req);
                return MyPostedRequestDto.fromEntity(req, bidderCount);
            } else {
                Optional<OfferEntity> finalOfferOpt = findFinalOffer(req);
                return MyPostedRequestDto.fromEntity(req, finalOfferOpt);
            }
        }).collect(Collectors.toList());
        
        // [✅ 핵심 추가] DTO 리스트를 itemName으로 최종 필터링합니다.
        List<MyPostedRequestDto> searchedList;
        if (itemName != null && !itemName.isBlank()) {
            searchedList = dtoList.stream()
                .filter(dto -> dto.getItemName().toLowerCase().contains(itemName.toLowerCase()))
                .collect(Collectors.toList());
        } else {
            searchedList = dtoList;
        }
        
        // 4. 상태(status) 탭 필터를 적용합니다.
        List<MyPostedRequestDto> filteredList;
        if (status != null && !status.isEmpty()) {
            filteredList = searchedList.stream()
                    .filter(dto -> {
                        // "OPEN" 탭은 OPEN 상태의 요청만 필터링
                        if ("OPEN".equalsIgnoreCase(status)) {
                            return "OPEN".equals(dto.getStatus());
                        }
                        // "CLOSED" 탭은 CLOSED 상태의 모든 요청을 포함
                        if ("CLOSED".equalsIgnoreCase(status)) {
                            return "CLOSED".equals(dto.getStatus());
                        }
                        // 그 외 (ACCEPTED, CONFIRMED 등)는 상세 상태(detailedStatus)로 필터링
                        return dto.getDetailedStatus() != null && status.equalsIgnoreCase(dto.getDetailedStatus());
                    })
                    .collect(Collectors.toList());
        } else {
            filteredList = searchedList;
        }

        // 5. 최종 목록으로 페이지네이션 객체를 만듭니다.
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), filteredList.size());
        List<MyPostedRequestDto> pageContent = (start > end) ? List.of() : filteredList.subList(start, end);
        
        return new PageImpl<>(pageContent, pageable, filteredList.size());
    }
    
    private Optional<OfferEntity> findFinalOffer(RequestEntity request) {
        Optional<OfferEntity> winningOfferOpt = offerRepository.findAllByRequest(request)
                .stream()
                .filter(o -> o.getStatus() != OfferStatus.PENDING && o.getStatus() != OfferStatus.REJECTED)
                .findFirst();

        if (winningOfferOpt.isPresent()) {
            OfferEntity winningOffer = winningOfferOpt.get();
            if (winningOffer.getStatus() == OfferStatus.RESOLD) {
                List<RequestEntity> nextRequests = requestRepository.findBySourceOfferOrderedByCreatedAtDesc(winningOffer);
                if (!nextRequests.isEmpty()) {
                    return findFinalOffer(nextRequests.get(0));
                }
            } else {
                return winningOfferOpt;
            }
        }
        return Optional.empty();
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
        
        // [✅ 아래 코드 추가]
        // 모든 제안의 상태가 변경된 후, 이벤트를 발행합니다.
        eventPublisher.publishEvent(new NotificationEvents.OfferConfirmedEvent(this, allOffers, winningOffer));
        chatService.createChatRoomForOffer(winningOffer);

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