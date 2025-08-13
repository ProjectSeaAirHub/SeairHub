// [✅ /service/TransactionHistoryService.java 파일 전체를 이 최종 코드로 교체해주세요]
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

    // [✅ 핵심 수정] '판매' 내역 조회 로직을 재판매 체인을 끝까지 추적하도록 변경합니다.
    public List<TransactionHistoryDto> getSalesHistory(String currentUserId, LocalDate startDate, LocalDate endDate, String keyword) {
        UserEntity user = userRepository.findByUserId(currentUserId); // 현재 사용자 (포워더 B)

        // 내가(B)가 다른 사람에게 한 제안 중, 최종적으로 성공한(거절/보류 제외) 모든 제안을 찾습니다.
        List<OfferStatus> successfulStatuses = List.of(OfferStatus.ACCEPTED, OfferStatus.CONFIRMED, OfferStatus.SHIPPED, OfferStatus.COMPLETED, OfferStatus.RESOLD);
        List<OfferEntity> mySuccessfulOffers = offerRepository.findByForwarderAndStatusIn(user, successfulStatuses);

        List<TransactionHistoryDto> salesHistory = new ArrayList<>();

        for (OfferEntity myOffer : mySuccessfulOffers) { // myOffer는 B가 A(또는 다른 포워더)에게 한 제안
            Optional<OfferEntity> finalOfferInChainOpt;

            // 만약 내 제안의 상태가 'RESOLD'라면, 재판매 체인의 최종 승자를 찾아야 합니다.
            if (myOffer.getStatus() == OfferStatus.RESOLD) {
                List<RequestEntity> resaleRequests = requestRepository.findBySourceOfferOrderedByCreatedAtDesc(myOffer);
                if (resaleRequests.isEmpty()) {
                    continue; // 재판매 요청이 없으면 건너뜀
                }
                // 내가 만든 재판매 요청으로부터 시작하여 최종 운송 포워더(C)를 찾습니다.
                finalOfferInChainOpt = findFinalOffer(resaleRequests.get(0));
            } else {
                // 'RESOLD'가 아니라면, 내가 직접 운송한 것이므로 내 제안이 최종 제안입니다.
                finalOfferInChainOpt = Optional.of(myOffer);
            }

            // 최종 운송을 담당한 컨테이너가 '정산완료' 상태인지 확인합니다.
            if (finalOfferInChainOpt.isPresent() && finalOfferInChainOpt.get().getContainer().getStatus() == ContainerStatus.SETTLED) {
                // 정산이 완료되었다면, 이 거래를 나의 '판매' 내역으로 기록합니다.
                TransactionHistoryDto dto = TransactionHistoryDto.builder()
                        .transactionDate(myOffer.getCreatedAt()) // 거래일은 내가 원 화주(A)와 계약한 날짜
                        .type("판매")
                        .itemName(myOffer.getRequest().getCargo().getItemName())
                        .departurePort(myOffer.getRequest().getDeparturePort())
                        .arrivalPort(myOffer.getRequest().getArrivalPort())
                        .partnerName(myOffer.getRequest().getRequester().getCompanyName()) // 거래 상대방은 원 화주(A)
                        .price(myOffer.getPrice()) // 가격은 내가 A에게 받은 금액
                        .currency(myOffer.getCurrency())
                        .status("정산완료")
                        .build();
                salesHistory.add(dto);
            }
        }

        // 필터링 로직은 그대로 유지합니다.
        return salesHistory.stream()
            .filter(dto -> (startDate == null || !dto.getTransactionDate().toLocalDate().isBefore(startDate)))
            .filter(dto -> (endDate == null || !dto.getTransactionDate().toLocalDate().isAfter(endDate)))
            .filter(dto -> (keyword == null || keyword.isBlank() ||
                    (dto.getItemName() != null && dto.getItemName().toLowerCase().contains(keyword.toLowerCase())) ||
                    (dto.getPartnerName() != null && dto.getPartnerName().toLowerCase().contains(keyword.toLowerCase()))))
            .collect(Collectors.toList());
    }

    // '구매' 내역 조회 로직 (기존과 동일, 재판매 건을 조회)
    public List<TransactionHistoryDto> getPurchaseHistory(String currentUserId, LocalDate startDate, LocalDate endDate, String keyword) {
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
                    .type("구매")
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
    
    // (이하 화주 이력 조회 및 재판매 체인 추적 로직은 기존과 동일)
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