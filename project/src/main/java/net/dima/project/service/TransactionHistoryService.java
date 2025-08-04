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

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransactionHistoryService {

    private final UserRepository userRepository;
    private final RequestRepository requestRepository;
    private final OfferRepository offerRepository;

    // ★★★ 1. 이 메서드가 아래 두 메서드에 파라미터를 전달합니다 ★★★
    public List<TransactionHistoryDto> getTransactionHistory(String currentUserId, LocalDate startDate, LocalDate endDate, String keyword) {
        List<TransactionHistoryDto> sales = getSalesHistory(currentUserId, startDate, endDate, keyword);
        List<TransactionHistoryDto> purchases = getPurchaseHistory(currentUserId, startDate, endDate, keyword);

        return Stream.concat(sales.stream(), purchases.stream())
                .sorted(Comparator.comparing(TransactionHistoryDto::getTransactionDate).reversed())
                .collect(Collectors.toList());
    }

    // ★★★ 2. getSalesHistory 메서드가 모든 파라미터를 받도록 수정 ★★★
    public List<TransactionHistoryDto> getSalesHistory(String currentUserId, LocalDate startDate, LocalDate endDate, String keyword) {
        UserEntity user = userRepository.findByUserId(currentUserId);
        List<RequestEntity> myResaleRequests = requestRepository.findByRequesterAndStatusAndSourceOfferIsNotNull(user, RequestStatus.CLOSED);

        return myResaleRequests.stream().map(req -> {
            OfferEntity winningOffer = offerRepository.findAllByRequest(req)
                    .stream()
                    .filter(o -> o.getStatus() != OfferStatus.PENDING && o.getStatus() != OfferStatus.REJECTED)
                    .findFirst()
                    .orElse(null);

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

    // ★★★ 3. getPurchaseHistory 메서드가 모든 파라미터를 받도록 수정 ★★★
    public List<TransactionHistoryDto> getPurchaseHistory(String currentUserId, LocalDate startDate, LocalDate endDate, String keyword) {
        UserEntity user = userRepository.findByUserId(currentUserId);
        List<OfferStatus> completedStatuses = List.of(
                OfferStatus.ACCEPTED, OfferStatus.CONFIRMED, OfferStatus.RESOLD,
                OfferStatus.SHIPPED, OfferStatus.COMPLETED);
        List<OfferEntity> myPurchases = offerRepository.findByForwarderAndStatusIn(user, completedStatuses);

        return myPurchases.stream()
                .filter(offer -> offer.getContainer() != null && offer.getContainer().getStatus() == ContainerStatus.SETTLED)
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
                .filter(dto -> (startDate == null || !dto.getTransactionDate().toLocalDate().isBefore(startDate)))
                .filter(dto -> (endDate == null || !dto.getTransactionDate().toLocalDate().isAfter(endDate)))
                .filter(dto -> (keyword == null || keyword.isBlank() ||
                        (dto.getItemName() != null && dto.getItemName().toLowerCase().contains(keyword.toLowerCase())) ||
                        (dto.getPartnerName() != null && dto.getPartnerName().toLowerCase().contains(keyword.toLowerCase()))))
                .collect(Collectors.toList());
    }

 // [✅ getShipperHistory 메서드 전체를 이 코드로 교체해주세요]
    public Page<TransactionHistoryDto> getShipperHistory(String currentUserId, LocalDate startDate, LocalDate endDate, String keyword, Pageable pageable) {
        UserEntity shipper = userRepository.findByUserId(currentUserId);
        List<RequestEntity> closedRequests = requestRepository.findByRequesterAndSourceOfferIsNullAndStatus(shipper, RequestStatus.CLOSED);

        List<TransactionHistoryDto> historyList = closedRequests.stream()
                .map(req -> {
                    Optional<OfferEntity> finalOfferOpt = findFinalOffer(req);
                    if (finalOfferOpt.isPresent() && finalOfferOpt.get().getContainer().getStatus() == ContainerStatus.SETTLED) {
                        OfferEntity finalOffer = finalOfferOpt.get();
                        return TransactionHistoryDto.builder()
                                .transactionDate(finalOffer.getCreatedAt())
                                .type("요청")
                                .itemName(req.getCargo().getItemName())
                                .departurePort(req.getDeparturePort())
                                .arrivalPort(req.getArrivalPort())
                                .partnerName(finalOffer.getForwarder().getCompanyName())
                                .price(finalOffer.getPrice())
                                .currency(finalOffer.getCurrency())
                                .status("정산완료")
                                .build();
                    }
                    return null;
                })
                .filter(dto -> dto != null)
                // [✅ 아래 필터링 로직 추가]
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

 // [✅ findFinalOffer 메서드 전체를 이 코드로 교체해주세요]
    private Optional<OfferEntity> findFinalOffer(RequestEntity request) {
        // 1. 현재 요청의 낙찰자를 찾음 (거절/보류 제외)
        Optional<OfferEntity> winningOfferOpt = offerRepository.findAllByRequest(request).stream()
                .filter(o -> o.getStatus() != OfferStatus.PENDING && o.getStatus() != OfferStatus.REJECTED)
                .findFirst();

        if (winningOfferOpt.isPresent()) {
            OfferEntity winningOffer = winningOfferOpt.get();
            // 2. 만약 낙찰된 제안이 '재판매' 상태라면, 다음 거래를 추적
            if (winningOffer.getStatus() == OfferStatus.RESOLD) {
                
                // ★★★ 핵심 수정 ★★★
                // 중복된 재판매 요청이 있더라도, 가장 최신 1건만 가져옵니다.
                List<RequestEntity> nextRequests = requestRepository.findBySourceOfferOrderedByCreatedAtDesc(winningOffer);
                
                // 가져온 요청이 있다면, 그 중 첫 번째(가장 최신) 요청으로 재귀 호출을 계속합니다.
                if (!nextRequests.isEmpty()) {
                    return findFinalOffer(nextRequests.get(0));
                }
            } else {
                // 3. '재판매'가 아니면, 이 제안이 최종 운송자이므로 반환
                return winningOfferOpt;
            }
        }
        return Optional.empty(); // 낙찰자가 없는 경우
    }
}