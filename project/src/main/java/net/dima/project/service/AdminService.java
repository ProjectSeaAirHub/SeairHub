package net.dima.project.service;

import lombok.RequiredArgsConstructor;
import net.dima.project.dto.DashboardMetricsDto;
import net.dima.project.dto.ForwarderInfoDto;
import net.dima.project.dto.UserInfoDto;
import net.dima.project.dto.VolumeDto;
import net.dima.project.entity.*;
import net.dima.project.repository.ContainerRepository;
import net.dima.project.repository.OfferRepository;
import net.dima.project.repository.RequestRepository;
import net.dima.project.repository.ScfiDataRepository;
import net.dima.project.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminService {

    private final OfferRepository offerRepository;
    private final ContainerRepository containerRepository;
    private final UserRepository userRepository;
    private final RequestRepository requestRepository;
    private final ScfiDataRepository scfiDataRepository; 
    
    // [추가] SCFI 데이터 저장 메서드
    public void saveScfiData(LocalDate recordDate, BigDecimal indexValue) {
        // 이미 해당 날짜의 데이터가 있는지 확인 (중복 방지)
        if (scfiDataRepository.findByRecordDate(recordDate).isPresent()) {
            throw new IllegalStateException("이미 해당 날짜의 데이터가 존재합니다.");
        }
        ScfiData scfiData = new ScfiData();
        scfiData.setRecordDate(recordDate);
        scfiData.setIndexValue(indexValue);
        scfiDataRepository.save(scfiData);
    }
    
    // [추가] 포워더 목록 조회
    @Transactional(readOnly = true)
    public List<ForwarderInfoDto> getForwarderList() {
    	List<String> targetRoles = List.of("ROLE_fwd", "ROLE_PENDING");
    	List<UserEntity> forwarders = userRepository.findByRolesIn(targetRoles);
        List<OfferStatus> acceptedStatuses = List.of(OfferStatus.ACCEPTED, OfferStatus.RESOLD, OfferStatus.CONFIRMED, OfferStatus.SHIPPED, OfferStatus.COMPLETED);

        return forwarders.stream().map(fwd -> {
            long containerCount = containerRepository.countByForwarder(fwd);
            long totalOffers = offerRepository.countByForwarder(fwd);
            long acceptedOffers = offerRepository.countByForwarderAndStatusIn(fwd, acceptedStatuses);
            return ForwarderInfoDto.from(fwd, containerCount, totalOffers, acceptedOffers);
        }).collect(Collectors.toList());
    }

 // [수정] 메서드 전체를 아래 코드로 교체합니다.
    public void updateUserStatus(Integer userSeq, String status) {
        UserEntity user = userRepository.findById(userSeq)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 1. 승인 상태(approvalStatus)를 변경합니다.
        user.setApprovalStatus(status);

        // 2. [핵심] 승인 상태에 따라 역할(roles)도 함께 변경합니다.
        if ("APPROVED".equals(status)) {
            // "승인 완료" 상태가 되면, 포워더 역할을 부여합니다.
            user.setRoles("ROLE_fwd");
        } else if ("SUSPENDED".equals(status) || "REJECTED".equals(status)) {
            // "계정 정지" 또는 "승인 거절" 상태가 되면,
            // 모든 기능 접근을 막기 위해 역할을 다시 "승인 대기"로 변경합니다.
            user.setRoles("ROLE_PENDING");
        }
        // "PENDING" 상태로 변경하는 경우는 별도의 역할 변경이 필요 없습니다.

        userRepository.save(user);
    }
    

    // [추가] 화주 목록 조회
    @Transactional(readOnly = true)
    public List<UserInfoDto> getUserList() {
        // [수정] "ROLE_cus" 역할을 가진 모든 유저를 조회하도록 변경
        List<UserEntity> users = userRepository.findByRolesIn(List.of("ROLE_cus"));

        return users.stream().map(user -> {
            long totalRequests = requestRepository.countByRequester(user);
            long completedDeals = requestRepository.countByRequesterAndStatus(user, RequestStatus.CLOSED);
            double totalCbm = requestRepository.sumTotalCbmByRequester(user);
            return UserInfoDto.from(user, totalRequests, completedDeals, totalCbm);
        }).collect(Collectors.toList());
    }
    
    // [추가] 대시보드 지표 계산 메서드
    @Transactional(readOnly = true)
    public DashboardMetricsDto getDashboardMetrics() {
    	LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX);
        LocalDateTime deadlineThreshold = LocalDateTime.now().plusDays(1); // 마감 1일 전

        long todayRequests = requestRepository.countByCreatedAtBetween(startOfDay, endOfDay);
        long todayDeals = offerRepository.countByStatusAndCreatedAtBetween(OfferStatus.ACCEPTED, startOfDay, endOfDay);

        long totalFwdUsers = userRepository.countByRoles("ROLE_fwd");
        long totalCusUsers = userRepository.countByRoles("ROLE_cus");
        long pendingUsers = userRepository.countByRoles("ROLE_PENDING"); // '승인 대기' 역할 기준
        long noBidRequests = requestRepository.countOpenRequestsWithNoBids(deadlineThreshold);
        
        
        // [✅ 아래 계산 로직을 추가해주세요]
        long missedConfirmations = requestRepository.countOpenRequestsWithBidsPastDeadline(now);
        long totalClosedOrExpired = requestRepository.countTotalClosedOrExpiredRequests(now);

        Double missedConfirmationRate = (totalClosedOrExpired == 0) ? 0.0 :
                ((double) missedConfirmations / totalClosedOrExpired) * 100;


        // [추가] SCFI 등락률 계산 로직
        List<ScfiData> latestTwoScfi = scfiDataRepository.findTop2ByOrderByRecordDateDesc();
        Double scfiChangePercentage = null;
        String scfiStatus = "NORMAL";

        if (latestTwoScfi.size() == 2) {
            BigDecimal latest = latestTwoScfi.get(0).getIndexValue();
            BigDecimal previous = latestTwoScfi.get(1).getIndexValue();

            if (previous.compareTo(BigDecimal.ZERO) != 0) {
                BigDecimal change = latest.subtract(previous);
                BigDecimal percentage = change.divide(previous, 4, RoundingMode.HALF_UP)
                                              .multiply(new BigDecimal("100"));
                scfiChangePercentage = percentage.doubleValue();

                if (scfiChangePercentage >= 5.0) {
                    scfiStatus = "GREEN";
                } else if (scfiChangePercentage <= -5.0) {
                    scfiStatus = "RED";
                }
            }
        }

        return DashboardMetricsDto.builder()
                .todayRequests(todayRequests)
                .todayDeals(todayDeals)
                .totalFwdUsers(totalFwdUsers)
                .totalCusUsers(totalCusUsers)
                .pendingUsers(pendingUsers)
                .noBidRequests(noBidRequests)
                .scfiChangePercentage(scfiChangePercentage) // [추가]
                .scfiStatus(scfiStatus)                   // [추가]
                .missedConfirmationRate(missedConfirmationRate)
                .build();
    }

    // 기존 물동량 그래프 계산 메서드
    @Transactional(readOnly = true)
    public VolumeDto getSystemVolume() {
        List<OfferEntity> offersInScheduledContainers = offerRepository.findAllByContainerStatus(ContainerStatus.SCHEDULED);
        LocalDateTime now = LocalDateTime.now();

        double confirmedCbm = 0;
        double resaleCbm = 0;
        double biddingCbm = 0;

        for (OfferEntity offer : offersInScheduledContainers) {
            OfferStatus status = offer.getStatus();
            double cbm = offer.getRequest().getCargo().getTotalCbm();

            if (status == OfferStatus.ACCEPTED) {
                confirmedCbm += cbm;
            } else if (status == OfferStatus.FOR_SALE) {
                resaleCbm += cbm;
            } else if (status == OfferStatus.PENDING && offer.getRequest().getDeadline().isAfter(now)) {
                biddingCbm += cbm;
            }
        }

        List<ContainerEntity> scheduledContainers = containerRepository.findByStatus(ContainerStatus.SCHEDULED);
        double totalScheduledCapacity = scheduledContainers.stream()
                .mapToDouble(ContainerEntity::getCapacityCbm)
                .sum();
        double totalUsedCbm = confirmedCbm + resaleCbm + biddingCbm;
        double availableCbm = totalScheduledCapacity - totalUsedCbm;

        return VolumeDto.builder()
                .confirmedCbm(confirmedCbm)
                .resaleCbm(resaleCbm)
                .biddingCbm(biddingCbm)
                .availableCbm(availableCbm)
                .build();
    }
}