package net.dima.project.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dima.project.controller.UserController;
import net.dima.project.dto.AvailableContainerDto;
import net.dima.project.dto.CargoDetailDto;
import net.dima.project.dto.ContainerStatusDto;
import net.dima.project.dto.CreateContainerDto;
import net.dima.project.dto.ExternalCargoDto;
import net.dima.project.entity.*;
import net.dima.project.repository.*;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;


import net.dima.project.entity.OfferStatus; 
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.Comparator; // [✅ import 추가]
import org.springframework.data.domain.Sort; // [✅ import 추가]
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ContainerService {

    private final UserController userController;

    private final ContainerRepository containerRepository;
    private final ContainerCargoRepository containerCargoRepository;
    private final OfferRepository offerRepository;
    private final UserRepository userRepository;
    private final RequestRepository requestRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ChatService chatService;


    


    @Transactional(readOnly = true)
    public List<ContainerStatusDto> getContainerStatuses(String currentUserId, Sort sort) { // [✅ Sort 파라미터 추가]
        UserEntity forwarder = userRepository.findByUserId(currentUserId);
        
        // [✅ 핵심 수정] 정렬 조건 분리
        // 'availableCbm'은 DB에 없는 계산 필드이므로, Java에서 직접 정렬해야 합니다.
        final String sortBy = sort.get().findFirst().map(Sort.Order::getProperty).orElse("containerId");
        final Sort.Direction direction = sort.get().findFirst().map(Sort.Order::getDirection).orElse(Sort.Direction.ASC);
        final LocalDateTime now = LocalDateTime.now();

        // DB에서 직접 정렬 가능한 필드는 DB에 위임하고, 아니라면 기본 정렬로 가져옵니다.
        Sort dbSort = sortBy.equals("availableCbm") ? Sort.by("containerId").ascending() : sort;
        List<ContainerEntity> myContainers = containerRepository.findByForwarderAndStatusNot(forwarder, ContainerStatus.SETTLED, dbSort);

        if (myContainers.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> myContainerIds = myContainers.stream().map(ContainerEntity::getContainerId).collect(Collectors.toList());
     // ★★★ 핵심 수정: 연결된 모든 상세 정보를 한 번에 조회하도록 변경 ★★★
        List<OfferEntity> allOffers = offerRepository.findAllByContainer_ContainerIdIn(myContainerIds);
        Map<String, List<OfferEntity>> offersByContainerId = allOffers.stream()
                .collect(Collectors.groupingBy(offer -> offer.getContainer().getContainerId()));

        List<ContainerStatusDto> dtos = myContainers.stream().map(container -> {
            ContainerStatusDto dto = ContainerStatusDto.fromEntity(container);
            List<OfferEntity> offers = offersByContainerId.getOrDefault(container.getContainerId(), new ArrayList<>());
            
            // ... (기존 CBM 계산 로직은 그대로) ...
            double confirmedCbmFromOffers = offers.stream()
                    .filter(o -> o.getStatus() == OfferStatus.ACCEPTED || o.getStatus() == OfferStatus.CONFIRMED || o.getStatus() == OfferStatus.SHIPPED || o.getStatus() == OfferStatus.COMPLETED)
                    .mapToDouble(o -> o.getRequest().getCargo().getTotalCbm())
                    .sum();
            
            double resaleCbm = offers.stream().filter(o -> o.getStatus() == OfferStatus.FOR_SALE).mapToDouble(o -> o.getRequest().getCargo().getTotalCbm()).sum();
            // [✅ 이 부분을 아래와 같이 수정해주세요]
            double biddingCbm = offers.stream()
                    .filter(o -> 
                        // 조건 1: 제안 상태가 'PENDING'이고,
                        o.getStatus() == OfferStatus.PENDING &&
                        // 조건 2: 요청의 마감일이 지나지 않았을 때만 필터링
                        o.getRequest().getDeadline().isAfter(now)
                    )
                    .mapToDouble(o -> o.getRequest().getCargo().getTotalCbm())
                    .sum();
            
            List<ContainerCargoEntity> externalCargos = containerCargoRepository.findExternalCargosByContainerId(container.getContainerId(), true);
            double externalCbm = externalCargos.stream().mapToDouble(ContainerCargoEntity::getCbmLoaded).sum();

            dto.setConfirmedCbm(confirmedCbmFromOffers + externalCbm);
            dto.setResaleCbm(resaleCbm);
            dto.setBiddingCbm(biddingCbm);
            dto.setAvailableCbm(dto.getTotalCapacity() - dto.getConfirmedCbm() - dto.getResaleCbm() - dto.getBiddingCbm());
            
            boolean isEmpty = (dto.getConfirmedCbm() + dto.getResaleCbm() + dto.getBiddingCbm()) == 0;
            boolean canConfirm = (dto.getResaleCbm() == 0 && dto.getBiddingCbm() == 0 && dto.getConfirmedCbm() > 0);

            dto.setStatus(container.getStatus());
            dto.setDeletable(isEmpty);
            dto.setConfirmable(canConfirm && container.getStatus() == ContainerStatus.SCHEDULED);
            
            return dto;
        }).collect(Collectors.toList());

        // [✅ 핵심 수정] '입찰가능물량순'일 경우, DTO 생성 후 Java 메모리에서 직접 정렬
        if (sortBy.equals("availableCbm")) {
            Comparator<ContainerStatusDto> comparator = Comparator.comparing(ContainerStatusDto::getAvailableCbm);
            if (direction == Sort.Direction.DESC) {
                comparator = comparator.reversed();
            }
            dtos.sort(comparator);
        }

        return dtos;
    }

    public List<AvailableContainerDto> getAvailableContainers(Long requestId, String currentUserId) {
        log.info("--------- [ContainerService] getAvailableContainers START ---------");
        log.info("요청 ID: {}, 현재 사용자 ID: {}", requestId, currentUserId);

        RequestEntity request = requestRepository.findRequestWithDetailsById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 요청입니다: " + requestId));
        log.info(">> 조회된 요청 정보: 경로({} -> {}), CBM({})", request.getDeparturePort(), request.getArrivalPort(), request.getCargo().getTotalCbm());

        UserEntity forwarder = Optional.ofNullable(userRepository.findByUserId(currentUserId))
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + currentUserId));
        log.info(">> 조회된 포워더 정보: {}", forwarder.getUserId());

        // [✅ 핵심 수정] findByForwarder 메서드 호출 시, 기본 정렬(Sort) 객체를 추가해줍니다.
        List<ContainerEntity> allMyContainers = containerRepository.findByForwarder(forwarder, Sort.by("containerId").ascending());

        log.info(">> '{}' 사용자의 전체 컨테이너 수: {}개", currentUserId, allMyContainers.size());

        allMyContainers.forEach(c -> 
            log.info("   - 보유 컨테이너: {}, 경로({} -> {})", c.getContainerId(), c.getDeparturePort(), c.getArrivalPort())
        );
        log.info(">> 경로 필터링 시작...");

        List<AvailableContainerDto> result = allMyContainers.stream()
                .filter(container -> {
                	 boolean isScheduled = container.getStatus() == ContainerStatus.SCHEDULED;
                     boolean departureMatch = container.getDeparturePort().trim().equalsIgnoreCase(request.getDeparturePort().trim());
                     boolean arrivalMatch = container.getArrivalPort().trim().equalsIgnoreCase(request.getArrivalPort().trim());
                     log.info("   - 필터링 검사: 컨테이너({}), 상태일치={}, 출발지 일치={}, 도착지 일치={}", 
                             container.getContainerId(), isScheduled, departureMatch, arrivalMatch);
                    
                    return isScheduled && departureMatch && arrivalMatch;
                })
                .map(container -> {
                    Double loadedCbm = Optional.ofNullable(containerCargoRepository.sumCbmByContainerId(container.getContainerId())).orElse(0.0);
                    double availableCbm = container.getCapacityCbm() - loadedCbm;
                    
                    return AvailableContainerDto.builder()
                            .containerId(container.getContainerId())
                            .containerDisplayName(String.format("%s (%s → %s)", container.getContainerId(), container.getDeparturePort(), container.getArrivalPort()))
                            .availableCbm(availableCbm)
                            .etd(container.getEtd())
                            .eta(container.getEta())
                            .build();
                })
                .collect(Collectors.toList());
        log.info(">> 필터링 후 최종 결과 컨테이너 수: {}개", result.size());
        log.info("--------- [ContainerService] getAvailableContainers END ---------");
        return result;
    }
    
    @Transactional
    public void addExternalCargo(ExternalCargoDto dto, String currentUserId) {
        ContainerEntity container = containerRepository.findById(dto.getContainerId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 컨테이너입니다."));
        UserEntity forwarder = userRepository.findByUserId(currentUserId);

        if (!container.getForwarder().getUserSeq().equals(forwarder.getUserSeq())) {
            throw new SecurityException("자신의 컨테이너에만 서류를 등록할 수 있습니다.");
        }

        double loadedCbm = containerCargoRepository.findByContainer_ContainerId(container.getContainerId())
                .stream()
                .mapToDouble(ContainerCargoEntity::getCbmLoaded)
                .sum();
        double availableCbm = container.getCapacityCbm() - loadedCbm;

        if (dto.getCbm() > availableCbm) {
            throw new IllegalArgumentException("컨테이너의 잔여 용량이 부족합니다. (잔여: " + String.format("%.2f", availableCbm) + " CBM)");
        }

        ContainerCargoEntity externalCargo = ContainerCargoEntity.builder()
                .container(container)
                .offer(null)
                .cbmLoaded(dto.getCbm())
                .isExternal(true)
                .externalCargoName(dto.getCargoName())
                .freightCost(dto.getPrice())
                .freightCurrency(dto.getCurrency())
                .build();
        
        containerCargoRepository.save(externalCargo);
    }
    
    @Transactional
    public void deleteExternalCargo(Long cargoId, String currentUserId) {
        ContainerCargoEntity cargo = containerCargoRepository.findById(cargoId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 화물 정보입니다."));

        if (!cargo.getIsExternal()) {
            throw new IllegalArgumentException("외부 등록 화물만 삭제할 수 있습니다.");
        }
        if (!cargo.getContainer().getForwarder().getUserId().equals(currentUserId)) {
            throw new SecurityException("삭제 권한이 없습니다.");
        }
        containerCargoRepository.delete(cargo);
    }

 // [✅ getDetailsForContainerStatus 메서드 전체를 이 코드로 교체해주세요]
    public List<CargoDetailDto> getDetailsForContainerStatus(String containerId, String statusString, String currentUserId) {
        UserEntity forwarder = userRepository.findByUserId(currentUserId);
        OfferStatus status = OfferStatus.valueOf(statusString.toUpperCase());
        List<CargoDetailDto> details = new ArrayList<>();

        // 1. [핵심 수정] 확정 이후 상태일 경우, 관련된 모든 상태의 화물을 조회합니다.
        if (status == OfferStatus.CONFIRMED || status == OfferStatus.SHIPPED || status == OfferStatus.COMPLETED) {
            List<OfferStatus> finalizedStatuses = List.of(OfferStatus.ACCEPTED, OfferStatus.CONFIRMED, OfferStatus.SHIPPED, OfferStatus.COMPLETED);
            List<OfferEntity> offers = offerRepository.findDetailsByContainerAndStatusInWithAllDetails(containerId, finalizedStatuses, forwarder);
            
            details.addAll(offers.stream().map(offer -> CargoDetailDto.builder()
                    .offerId(offer.getOfferId())
                    .itemName(offer.getRequest().getCargo().getItemName())
                    .cbm(offer.getRequest().getCargo().getTotalCbm())
                    .freightCost(offer.getPrice())
                    .freightCurrency(offer.getCurrency())
                    .status(offer.getStatus().name())
                    .external(false)
                    .deadline(offer.getRequest().getDeadline())
                    .build()).collect(Collectors.toList()));
        
        // 2. [핵심 수정] 확정 전 상태(입찰중, 재판매중)는 기존 로직을 그대로 사용합니다.
        } else {
            List<OfferEntity> offers = offerRepository.findDetailsByContainerAndStatusWithAllDetails(containerId, status, forwarder);
            Map<Long, Long> resaleRequestIdMap = new java.util.HashMap<>();
            if (status == OfferStatus.FOR_SALE && !offers.isEmpty()) {
                List<RequestEntity> resaleRequests = requestRepository.findBySourceOfferIn(offers);
                resaleRequests.forEach(req -> resaleRequestIdMap.put(req.getSourceOffer().getOfferId(), req.getRequestId()));
            }
            details.addAll(offers.stream().map(offer -> CargoDetailDto.builder()
                    .offerId(offer.getOfferId())
                    .itemName(offer.getRequest().getCargo().getItemName())
                    .cbm(offer.getRequest().getCargo().getTotalCbm())
                    .freightCost(offer.getPrice())
                    .freightCurrency(offer.getCurrency())
                    .status(offer.getStatus().name())
                    .external(false)
                    .deadline(offer.getRequest().getDeadline())
                    .resaleRequestId(resaleRequestIdMap.get(offer.getOfferId()))
                    .build()).collect(Collectors.toList()));
        }

        // 3. [핵심 수정] 외부 화물 조회 로직을 분리하여 확정 이후 상태일 때 항상 포함되도록 합니다.
        if (status == OfferStatus.ACCEPTED || status == OfferStatus.CONFIRMED || status == OfferStatus.SHIPPED || status == OfferStatus.COMPLETED) {
            List<ContainerCargoEntity> externalCargos = containerCargoRepository.findExternalCargosByContainerId(containerId, true);
            for (ContainerCargoEntity cargo : externalCargos) {
                details.add(CargoDetailDto.builder()
                        .offerId(cargo.getId())
                        .itemName(cargo.getExternalCargoName())
                        .cbm(cargo.getCbmLoaded())
                        .freightCost(cargo.getFreightCost())
                        .freightCurrency(cargo.getFreightCurrency())
                        .status(status.name())
                        .external(true)
                        .deadline(null)
                        .build());
            }
        }
        return details;
    }
    
    // [✅ 추가] 새로운 컨테이너 생성 서비스 로직
    @Transactional // 쓰기 작업이므로 @Transactional을 붙여 읽기/쓰기 모드로 전환
    public void createContainer(CreateContainerDto dto, String currentUserId) {
        UserEntity forwarder = userRepository.findByUserId(currentUserId);

        String containerId = "SEAU" + String.format("%07d", ThreadLocalRandom.current().nextInt(10000000));

        double capacityCbm = 0;
        if ("20ft".equals(dto.getSize())) {
            capacityCbm = 26.0;
        } else if ("40ft".equals(dto.getSize())) {
            capacityCbm = 55.0;
        } else {
            throw new IllegalArgumentException("컨테이너 사이즈는 20ft 또는 40ft만 가능합니다.");
        }

        ContainerEntity newContainer = ContainerEntity.builder()
                .containerId(containerId)
                .forwarder(forwarder)
                .departurePort(dto.getDeparturePort())
                .arrivalPort(dto.getArrivalPort())
                .etd(dto.getEtd())
                .eta(dto.getEta())
                .size(dto.getSize())
                .capacityCbm(capacityCbm)
                .status(ContainerStatus.SCHEDULED)
                .build();

        containerRepository.save(newContainer);
    }
    

    // [✅ 이 메서드 전체를 아래 코드로 교체해주세요]
    
    @Transactional
    public void deleteContainer(String containerId, String currentUserId) {
        // 1. 컨테이너와 사용자 권한 확인
        ContainerEntity container = containerRepository.findById(containerId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 컨테이너입니다."));
        if (!container.getForwarder().getUserId().equals(currentUserId)) {
            throw new SecurityException("삭제 권한이 없습니다.");
        }

        // 2. 이 컨테이너와 연결된 모든 제안(Offer)과 화물(ContainerCargo)을 조회합니다.
        List<OfferEntity> associatedOffers = offerRepository.findAllByContainer(container);
        List<ContainerCargoEntity> associatedCargos = containerCargoRepository.findAllByContainer(container);

        // 3. 삭제할 제안(Offer)들을 참조하는 재판매 요청(Request)과의 연결을 먼저 끊습니다.
        for (OfferEntity offer : associatedOffers) {
            requestRepository.findBySourceOffer(offer)
                .ifPresent(request -> request.setSourceOffer(null));
        }
        
        // 4. [✅ 핵심 수정] deleteAllInBatch -> deleteAll 로 변경
        //    JPA가 상태 변경을 인지하고 올바른 순서로 쿼리를 실행하도록 합니다.
        if (!associatedOffers.isEmpty()) {
            offerRepository.deleteAll(associatedOffers);
        }
        if (!associatedCargos.isEmpty()) {
            containerCargoRepository.deleteAll(associatedCargos);
        }

        // 5. 모든 연결이 정리되었으므로 컨테이너를 최종적으로 삭제합니다.
        containerRepository.delete(container);
    }
    
    @Transactional
    public void confirmContainer(String containerId, String currentUserId) {
        ContainerEntity container = containerRepository.findById(containerId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 컨테이너입니다."));
        // 권한 확인
        if (!container.getForwarder().getUserId().equals(currentUserId)) {
            throw new SecurityException("확정 권한이 없습니다.");
        }
        
        List<OfferEntity> offers = offerRepository.findAllByContainer(container);
        boolean hasPendingOrForSale = offers.stream()
                .anyMatch(o -> o.getStatus() == OfferStatus.PENDING || o.getStatus() == OfferStatus.FOR_SALE);
        if (hasPendingOrForSale) {
            throw new IllegalStateException("재판매중이거나 입찰중인 화물이 있어 확정할 수 없습니다.");
        }
        
        // 1. 컨테이너 상태를 'CONFIRMED'로 변경
        container.setStatus(ContainerStatus.CONFIRMED); // [✅ 수정]

        // [✅ 2. 핵심 추가] 이 컨테이너에 속한 'ACCEPTED' 상태의 모든 제안을 'CONFIRMED'로 변경
        for (OfferEntity offer : offers) {
            if (offer.getStatus() == OfferStatus.ACCEPTED) {
                offer.setStatus(OfferStatus.CONFIRMED);
            }
        }
        
        // [✅ 아래 코드 추가]
        eventPublisher.publishEvent(new NotificationEvents.ContainerStatusChangedEvent(this, container, "컨테이너가 확정되었습니다."));
    }
    
    
    // [✅ 추가] 선적완료 처리 메서드
    @Transactional // [✅ 이 메서드에 @Transactional 추가]
    public void shipContainer(String containerId, String currentUserId) {
        ContainerEntity container = findAndValidateContainer(containerId, currentUserId);
        if (container.getStatus() != ContainerStatus.CONFIRMED) {
            throw new IllegalStateException("'확정' 상태의 컨테이너만 선적할 수 있습니다.");
        }
        container.setStatus(ContainerStatus.SHIPPED);
        
        // [✅ 추가] 이 컨테이너에 연결된 CONFIRMED 상태의 모든 제안을 SHIPPED로 변경
        List<OfferEntity> offersToUpdate = offerRepository.findAllByContainer(container);
        for (OfferEntity offer : offersToUpdate) {
            if (offer.getStatus() == OfferStatus.CONFIRMED) {
                offer.setStatus(OfferStatus.SHIPPED);
            }
        }
        
        // [✅ 아래 코드 추가]
        eventPublisher.publishEvent(new NotificationEvents.ContainerStatusChangedEvent(this, container, "선적이 완료되었습니다."));
    }

    // [✅ 추가] 운송완료 처리 메서드
    @Transactional // [✅ 이 메서드에 @Transactional 추가]
    public void completeShipment(String containerId, String currentUserId) {
        ContainerEntity container = findAndValidateContainer(containerId, currentUserId);
        if (container.getStatus() != ContainerStatus.SHIPPED) {
            throw new IllegalStateException("'선적완료' 상태의 컨테이너만 운송완료 처리할 수 있습니다.");
        }
        container.setStatus(ContainerStatus.COMPLETED);
        
        // [✅ 추가] 이 컨테이너에 연결된 SHIPPED 상태의 모든 제안을 COMPLETED로 변경
        List<OfferEntity> offersToUpdate = offerRepository.findAllByContainer(container);
        for (OfferEntity offer : offersToUpdate) {
            if (offer.getStatus() == OfferStatus.SHIPPED) {
                offer.setStatus(OfferStatus.COMPLETED);
            }
        }
        // [✅ 아래 코드 추가]
        eventPublisher.publishEvent(new NotificationEvents.ContainerStatusChangedEvent(this, container, "운송이 완료되었습니다."));
    }

    // [✅ 추가] 중복 코드를 줄이기 위한 헬퍼 메서드
    private ContainerEntity findAndValidateContainer(String containerId, String currentUserId) {
        ContainerEntity container = containerRepository.findById(containerId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 컨테이너입니다."));
        if (!container.getForwarder().getUserId().equals(currentUserId)) {
            throw new SecurityException("권한이 없습니다.");
        }
        return container;
    }
    
 // ContainerService 클래스 내부에 아래 메서드를 추가하세요.
    @Transactional
    public void settleContainer(String containerId, String currentUserId) {
        ContainerEntity container = findAndValidateContainer(containerId, currentUserId);
        if (container.getStatus() != ContainerStatus.COMPLETED) {
            throw new IllegalStateException("'운송완료' 상태의 컨테이너만 정산할 수 있습니다.");
        }
        container.setStatus(ContainerStatus.SETTLED);
        // [✅ 아래 코드 추가]
        eventPublisher.publishEvent(new NotificationEvents.ContainerStatusChangedEvent(this, container, "정산이 완료되었습니다."));
        chatService.closeChatRoomsForSettledContainer(container);

    }
    
}