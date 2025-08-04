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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OfferService {

    private final OfferRepository offerRepository;
    private final RequestRepository requestRepository;
    private final UserRepository userRepository;
    private final ContainerRepository containerRepository;

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

        Specification<OfferEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("forwarder"), forwarder));

            // 1. [핵심 수정] DB 조회 시에는 상태 필터링을 하지 않도록 변경합니다.
            //    먼저 '진행중'과 '거절' 상태를 모두 가져와야 DTO 변환 시 마감 여부를 판단할 수 있습니다.
            List<OfferStatus> includedStatuses = List.of(
                OfferStatus.PENDING, 
                OfferStatus.REJECTED, 
                OfferStatus.ACCEPTED, 
                OfferStatus.FOR_SALE
            );
            predicates.add(root.get("status").in(includedStatuses));

            // 키워드 검색 로직은 그대로 유지
            if (keyword != null && !keyword.isBlank()) {
                Join<OfferEntity, RequestEntity> requestJoin = root.join("request");
                Join<RequestEntity, CargoEntity> cargoJoin = requestJoin.join("cargo");
                Predicate keywordPredicate;
                if (keyword.matches("\\d+")) {
                    keywordPredicate = cb.equal(requestJoin.get("requestId"), Long.parseLong(keyword));
                } else {
                    keywordPredicate = cb.like(cargoJoin.get("itemName"), "%" + keyword + "%");
                }
                predicates.add(keywordPredicate);
            }
            
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("request").fetch("cargo");
                root.fetch("container");
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        List<OfferEntity> allOfferEntities = offerRepository.findAll(spec, pageable.getSort());

        // 2. DTO로 변환합니다. (이 과정에서 마감된 '진행중'이 '거절'로 바뀝니다)
        List<MyOfferDto> allDtos = allOfferEntities.stream()
                .map(MyOfferDto::fromEntity)
                .collect(Collectors.toList());

        // 3. [핵심] DTO로 변환된 '최종 상태값'을 기준으로 필터링합니다.
        List<MyOfferDto> filteredDtos;
        if (status != null && !status.isEmpty()) {
            filteredDtos = allDtos.stream()
                .filter(dto -> status.equalsIgnoreCase(dto.getStatus()))
                .collect(Collectors.toList());
        } else {
            filteredDtos = allDtos; // 필터가 없으면 전체 목록 사용
        }

        // 4. 필터링된 최종 목록을 가지고 수동으로 페이지네이션 객체를 만듭니다.
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), filteredDtos.size());
        List<MyOfferDto> pageContent = (start > end) ? List.of() : filteredDtos.subList(start, end);
        
        return new PageImpl<>(pageContent, pageable, filteredDtos.size());
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