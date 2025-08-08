package net.dima.project.service;

import lombok.RequiredArgsConstructor;
import net.dima.project.dto.MyOfferDetailDto;
import net.dima.project.dto.MyOfferDto;
import net.dima.project.dto.OfferDto;
import net.dima.project.dto.OfferRequestDto;
import net.dima.project.dto.UpdateOfferDto;
import net.dima.project.entity.*;
import net.dima.project.repository.ContainerRepository;
import net.dima.project.repository.OfferRepository;
import net.dima.project.repository.RequestRepository;
import net.dima.project.repository.UserRepository;

import org.springframework.data.domain.Page;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Join;       // [✅ 이 줄을 추가해주세요]
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageImpl; // [✅ PageImpl import 추가]
import java.util.stream.Collectors; // [✅ Collectors import 추가]
import java.time.LocalDateTime;
import java.util.ArrayList; // [✅ import 추가]

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.dima.project.entity.OfferStatus;
import org.springframework.context.ApplicationEventPublisher;
import net.dima.project.entity.NotificationEvents;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OfferService {

    private final OfferRepository offerRepository;
    private final RequestRepository requestRepository;
    private final UserRepository userRepository;
    private final ContainerRepository containerRepository;
    private final ApplicationEventPublisher eventPublisher; 

    /**
     * 새로운 제안(Offer)을 생성합니다.
     */
    @Transactional
    public void createOffer(OfferRequestDto offerDto, String currentUserId) {
        RequestEntity request = requestRepository.findById(offerDto.getRequestId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 요청입니다."));
        UserEntity forwarder = Optional.ofNullable(userRepository.findByUserId(currentUserId))
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));
        ContainerEntity container = containerRepository.findById(offerDto.getContainerId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 컨테이너입니다."));
        
        // [✅ 핵심] 중복 제안 방지 로직
        if (offerRepository.existsByRequestAndForwarder(request, forwarder)) {
            throw new IllegalStateException("이미 해당 요청에 대한 제안을 제출했습니다.");
        }

        OfferEntity newOffer = OfferEntity.builder()
                .request(request)
                .container(container)
                .forwarder(forwarder)
                .price(offerDto.getPrice())
                .currency(offerDto.getCurrency())
                .status(OfferStatus.PENDING)
                .build();
        offerRepository.save(newOffer);
        
        // [✅ 아래 코드 추가]
        // 제안이 성공적으로 생성되면 이벤트를 발행합니다.
        eventPublisher.publishEvent(new NotificationEvents.OfferCreatedEvent(this, newOffer));
    
    }
    
    

    /**
     * 현재 로그인한 사용자의 모든 제안 목록을 조회합니다.
     */
    /**
     * [수정] 현재 로그인한 사용자의 모든 제안 목록을 필터링, 정렬, 페이징하여 조회합니다.
     */
 // OfferService.java의 getMyOffers 메서드 내부
    // ▼▼▼ getMyOffers 메서드 전체를 아래 코드로 교체해주세요 ▼▼▼
    public Page<MyOfferDto> getMyOffers(String currentUserId, String status, String keyword, Pageable pageable) {
        UserEntity forwarder = userRepository.findByUserId(currentUserId);
        LocalDateTime now = LocalDateTime.now();

        // [✅ 핵심 수정] Specification을 사용하여 DB에서 직접 필터링 조건을 처리합니다.
        Specification<OfferEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("forwarder"), forwarder));

            // 1. 상태(status) 필터링 로직
            if (status != null && !status.isEmpty()) {
                if ("PENDING".equalsIgnoreCase(status)) {
                    // '진행중' 탭: PENDING 상태이면서 마감일이 지나지 않은 건
                    predicates.add(cb.equal(root.get("status"), OfferStatus.PENDING));
                    predicates.add(cb.greaterThan(root.get("request").get("deadline"), now));
                } else if ("REJECTED".equalsIgnoreCase(status)) {
                    // '거절' 탭: REJECTED 상태이거나, PENDING 상태이지만 마감일이 지난 건
                    Predicate rejectedStatus = cb.equal(root.get("status"), OfferStatus.REJECTED);
                    Predicate expiredPending = cb.and(
                        cb.equal(root.get("status"), OfferStatus.PENDING),
                        cb.lessThanOrEqualTo(root.get("request").get("deadline"), now)
                    );
                    predicates.add(cb.or(rejectedStatus, expiredPending));
                }else if ("CONFIRMED".equalsIgnoreCase(status)) {
                    // [✅ 수정] '확정' 탭: CONFIRMED 또는 COMPLETED 상태인 건
                    predicates.add(root.get("status").in(OfferStatus.CONFIRMED, OfferStatus.COMPLETED));
                }else {
                    // 그 외 (수락, 재판매중)
                    try {
                        predicates.add(cb.equal(root.get("status"), OfferStatus.valueOf(status.toUpperCase())));
                    } catch (IllegalArgumentException e) {
                        // 잘못된 status 값이 들어올 경우 무시
                    }
                }
            } else {
                // '전체' 탭: 정산 완료를 제외한 모든 제안
                 List<OfferStatus> includedStatuses = List.of(
                    OfferStatus.PENDING, 
                    OfferStatus.REJECTED, 
                    OfferStatus.ACCEPTED, 
                    OfferStatus.FOR_SALE,
                    OfferStatus.RESOLD,
                    OfferStatus.CONFIRMED,
                    OfferStatus.SHIPPED,
                    OfferStatus.COMPLETED
                );
                predicates.add(root.get("status").in(includedStatuses));
            }

            // 2. 키워드 검색 로직 (기존과 동일)
            if (keyword != null && !keyword.isBlank()) {
                Join<OfferEntity, RequestEntity> requestJoin = root.join("request");
                Join<RequestEntity, CargoEntity> cargoJoin = requestJoin.join("cargo");
                Predicate keywordPredicate;
                if (keyword.matches("\\d+")) {
                    keywordPredicate = cb.like(requestJoin.get("requestId").as(String.class), "%" + keyword + "%");
                } else {
                    keywordPredicate = cb.like(cargoJoin.get("itemName"), "%" + keyword + "%");
                }
                predicates.add(keywordPredicate);
            }
            
            // N+1 문제 방지를 위한 fetch join (기존과 동일)
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("request").fetch("cargo");
                root.fetch("container");
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        // [✅ 핵심 수정] DB에서 직접 페이징하여 필요한 만큼의 데이터만 가져옵니다.
        Page<OfferEntity> offerPage = offerRepository.findAll(spec, pageable);

        // [✅ 핵심 수정] DTO 변환 로직은 그대로 사용하되, Page 객체의 map 기능을 활용합니다.
        return offerPage.map(MyOfferDto::fromEntity);
    }
    
    /**
     * '나의제안조회' 상세보기를 위한 서비스 로직
     */
    // ▼▼▼ getMyOfferDetails 메서드를 아래 코드로 교체해주세요 ▼▼▼
    public MyOfferDetailDto getMyOfferDetails(Long offerId, String currentUserId) {
        OfferEntity myOffer = offerRepository.findByIdWithDetails(offerId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 제안입니다: " + offerId));
        if (!myOffer.getForwarder().getUserId().equals(currentUserId)) {
            throw new SecurityException("자신의 제안만 조회할 수 있습니다.");
        }

        Optional<OfferEntity> winningOfferOpt = Optional.empty();

        // 내 제안이 거절되었거나, 마감 시간 초과로 거절 처리된 경우
        boolean isRejected = myOffer.getStatus() == OfferStatus.REJECTED ||
                             (myOffer.getStatus() == OfferStatus.PENDING && LocalDateTime.now().isAfter(myOffer.getRequest().getDeadline()));

        if (isRejected) {
            // 원본 요청에 대한 최종 낙찰자를 조회
            winningOfferOpt = offerRepository.findWinningOfferForRequest(myOffer.getRequest());
        }

        return MyOfferDetailDto.fromEntity(myOffer, winningOfferOpt);
    }
     
     
     @Transactional // [✅ 2. 쓰기 작업이므로 @Transactional을 붙여 읽기/쓰기 모드로 전환]
     public void updateOfferPrice(Long offerId, UpdateOfferDto dto, String currentUserId) {
         OfferEntity offer = offerRepository.findByIdWithDetails(offerId)
                 .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 제안입니다."));

         if (!offer.getForwarder().getUserId().equals(currentUserId)) {
             throw new SecurityException("자신의 제안만 수정할 수 있습니다.");
         }
         if (offer.getStatus() != OfferStatus.PENDING) {
             throw new IllegalStateException("'진행중' 상태의 제안만 수정할 수 있습니다.");
         }

         offer.setPrice(dto.getPrice());
         offer.setCurrency(dto.getCurrency());
     }

     @Transactional // [✅ 2. 쓰기 작업이므로 @Transactional을 붙여 읽기/쓰기 모드로 전환]
     public void cancelOffer(Long offerId, String currentUserId) {
         OfferEntity offer = offerRepository.findByIdWithDetails(offerId)
                 .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 제안입니다."));

         if (!offer.getForwarder().getUserId().equals(currentUserId)) {
             throw new SecurityException("자신의 제안만 취소할 수 있습니다.");
         }
         if (offer.getStatus() != OfferStatus.PENDING) {
             throw new IllegalStateException("'진행중' 상태의 제안만 취소할 수 있습니다.");
         }
         offerRepository.delete(offer);
     }
     
     /**
      * 화주가 자신의 요청에 온 제안 목록을 조회합니다.
      */
     public List<OfferDto> getOffersForShipperRequest(Long requestId, String currentUserId) {
         RequestEntity request = requestRepository.findById(requestId)
                 .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 요청입니다."));
         
         // 요청의 소유자가 맞는지 확인
         if (!request.getRequester().getUserId().equals(currentUserId)) {
             throw new SecurityException("자신의 요청에 대한 제안만 조회할 수 있습니다.");
         }

         return offerRepository.findAllByRequest(request).stream()
                 .map(OfferDto::fromEntity)
                 .collect(Collectors.toList());
     }
     
}