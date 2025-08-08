// [✅ TransactionHistoryService.java 파일 전체를 이 코드로 교체해주세요]
package net.dima.project.service;

import lombok.RequiredArgsConstructor;
import net.dima.project.dto.TransactionHistoryDto;
import net.dima.project.entity.*;
import net.dima.project.repository.OfferRepository;
import net.dima.project.repository.RequestRepository;
import net.dima.project.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransactionHistoryService {

    private final UserRepository userRepository;
    private final RequestRepository requestRepository;
    private final OfferRepository offerRepository;

    public List<TransactionHistoryDto> getTransactionHistory(String currentUserId, LocalDate startDate, LocalDate endDate, String keyword) {
        List<TransactionHistoryDto> sales = getSalesHistory(currentUserId, startDate, endDate, keyword);
        List<TransactionHistoryDto> purchases = getPurchaseHistory(currentUserId, startDate, endDate, keyword);

        return Stream.concat(sales.stream(), purchases.stream())
                .sorted(Comparator.comparing(TransactionHistoryDto::getTransactionDate).reversed())
                .collect(Collectors.toList());
    }

    public List<TransactionHistoryDto> getSalesHistory(String currentUserId, LocalDate startDate, LocalDate endDate, String keyword) {
        UserEntity user = userRepository.findByUserId(currentUserId);
        List<RequestEntity> myResaleRequests = requestRepository.findByRequesterAndStatusAndSourceOfferIsNotNull(user, RequestStatus.CLOSED);

        Map<Long, OfferEntity> winningOffersMap = offerRepository.findWinningOffersForRequests(myResaleRequests).stream()
                .collect(Collectors.toMap(offer -> offer.getRequest().getRequestId(), Function.identity()));

        return myResaleRequests.stream().map(req -> {
            OfferEntity winningOffer = winningOffersMap.get(req.getRequestId());

            if (winningOffer == null || winningOffer.getContainer().getStatus() != ContainerStatus.SETTLED) {
                return null;
            }
            
            return TransactionHistoryDto.builder()
                    .transactionDate(winningOffer.getCreatedAt())
                    .type("판매")
                    .itemName(req.getCargo().getItemName())
                    .departurePort(req.getDeparturePort())
                    .arrivalPort(req.getArrivalPort())
                    .partnerName(winningOffer.getForwarder().getCompanyName())
                    .price(winningOffer.getPrice())
                    .currency(winningOffer.getCurrency())
                    .status("정산완료")
                    .build();
        })
        .filter(dto -> dto != null)
        .filter(dto -> (startDate == null || !dto.getTransactionDate().toLocalDate().isBefore(startDate)))
        .filter(dto -> (endDate == null || !dto.getTransactionDate().toLocalDate().isAfter(endDate)))
        .filter(dto -> (keyword == null || keyword.isBlank() ||
                (dto.getItemName() != null && dto.getItemName().toLowerCase().contains(keyword.toLowerCase())) ||
                (dto.getPartnerName() != null && dto.getPartnerName().toLowerCase().contains(keyword.toLowerCase()))))
        .collect(Collectors.toList());
    }

    public List<TransactionHistoryDto> getPurchaseHistory(String currentUserId, LocalDate startDate, LocalDate endDate, String keyword) {
        UserEntity user = userRepository.findByUserId(currentUserId);
        
        Specification<OfferEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("forwarder"), user));
            predicates.add(cb.equal(root.get("container").get("status"), ContainerStatus.SETTLED));
            
            List<OfferStatus> completedStatuses = List.of(
                OfferStatus.ACCEPTED, OfferStatus.CONFIRMED, OfferStatus.RESOLD,
                OfferStatus.SHIPPED, OfferStatus.COMPLETED);
            predicates.add(root.get("status").in(completedStatuses));

            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate.atStartOfDay()));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate.atTime(23, 59, 59)));
            }
            if (keyword != null && !keyword.isBlank()) {
                Join<OfferEntity, RequestEntity> requestJoin = root.join("request");
                Join<RequestEntity, CargoEntity> cargoJoin = requestJoin.join("cargo");
                predicates.add(cb.or(
                    cb.like(cargoJoin.get("itemName"), "%" + keyword + "%"),
                    cb.like(requestJoin.get("requester").get("companyName"), "%" + keyword + "%")
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return offerRepository.findAll(spec).stream()
                .map(offer ->
                        TransactionHistoryDto.builder()
                                .transactionDate(offer.getCreatedAt())
                                .type("구매")
                                .itemName(offer.getRequest().getCargo().getItemName())
                                .departurePort(offer.getRequest().getDeparturePort())
                                .arrivalPort(offer.getRequest().getArrivalPort())
                                .partnerName(offer.getRequest().getRequester().getCompanyName())
                                .price(offer.getPrice())
                                .currency(offer.getCurrency())
                                .status("정산완료")
                                .build()
                )
                .collect(Collectors.toList());
    }

    public Page<TransactionHistoryDto> getShipperHistory(String currentUserId, LocalDate startDate, LocalDate endDate, String keyword, Pageable pageable) {
        UserEntity shipper = userRepository.findByUserId(currentUserId);
        List<RequestEntity> closedRequests = requestRepository.findByRequesterAndSourceOfferIsNullAndStatus(shipper, RequestStatus.CLOSED);
        
        Map<Long, OfferEntity> winningOffersMap = offerRepository.findWinningOffersForRequests(closedRequests).stream()
                .collect(Collectors.toMap(offer -> offer.getRequest().getRequestId(), Function.identity(), (o1, o2) -> o1));

        Map<Long, Optional<OfferEntity>> finalOffersMap = closedRequests.stream()
                .collect(Collectors.toMap(RequestEntity::getRequestId, this::findFinalOffer));

        List<TransactionHistoryDto> historyList = closedRequests.stream()
                .map(req -> {
                    Optional<OfferEntity> directWinningOfferOpt = Optional.ofNullable(winningOffersMap.get(req.getRequestId()));
                    Optional<OfferEntity> finalOfferOpt = finalOffersMap.getOrDefault(req.getRequestId(), Optional.empty());

                    if (directWinningOfferOpt.isPresent() && finalOfferOpt.isPresent() && finalOfferOpt.get().getContainer().getStatus() == ContainerStatus.SETTLED) {
                        OfferEntity directWinningOffer = directWinningOfferOpt.get();
                        
                        return TransactionHistoryDto.builder()
                                .transactionDate(directWinningOffer.getCreatedAt())
                                .type("요청")
                                .itemName(req.getCargo().getItemName())
                                .departurePort(req.getDeparturePort())
                                .arrivalPort(req.getArrivalPort())
                                .partnerName(directWinningOffer.getForwarder().getCompanyName())
                                .price(directWinningOffer.getPrice())
                                .currency(directWinningOffer.getCurrency())
                                .status("정산완료")
                                .build();
                    }
                    return null;
                })
                .filter(dto -> dto != null)
                .filter(dto -> (startDate == null || !dto.getTransactionDate().toLocalDate().isBefore(startDate)))
                .filter(dto -> (endDate == null || !dto.getTransactionDate().toLocalDate().isAfter(endDate)))
                .filter(dto -> (keyword == null || keyword.isBlank() ||
                        (dto.getItemName() != null && dto.getItemName().toLowerCase().contains(keyword.toLowerCase())) ||
                        (dto.getPartnerName() != null && dto.getPartnerName().toLowerCase().contains(keyword.toLowerCase()))))
                .sorted(Comparator.comparing(TransactionHistoryDto::getTransactionDate).reversed())
                .collect(Collectors.toList());

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), historyList.size());
        List<TransactionHistoryDto> pageContent = (start > end) ? List.of() : historyList.subList(start, end);

        return new PageImpl<>(pageContent, pageable, historyList.size());
    }

    private Optional<OfferEntity> findFinalOffer(RequestEntity request) {
        RequestEntity currentRequest = request;
        while (true) {
            Optional<OfferEntity> winningOfferOpt = offerRepository.findAllByRequest(currentRequest).stream()
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