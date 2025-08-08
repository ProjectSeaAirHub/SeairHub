// [✅ ContainerService.java 파일 전체를 이 최종 코드로 교체해주세요]
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

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

    public List<ContainerStatusDto> getContainerStatuses(String currentUserId, Sort sort) {
        UserEntity forwarder = userRepository.findByUserId(currentUserId);
        
        final String sortBy = sort.get().findFirst().map(Sort.Order::getProperty).orElse("containerId");
        final Sort.Direction direction = sort.get().findFirst().map(Sort.Order::getDirection).orElse(Sort.Direction.ASC);
        final LocalDateTime now = LocalDateTime.now();

        Sort dbSort = sortBy.equals("availableCbm") ? Sort.by("containerId").ascending() : sort;
        List<ContainerEntity> myContainers = containerRepository.findByForwarderAndStatusNot(forwarder, ContainerStatus.SETTLED, dbSort);

        if (myContainers.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> myContainerIds = myContainers.stream().map(ContainerEntity::getContainerId).collect(Collectors.toList());
        
        List<OfferEntity> allOffers = offerRepository.findAllByContainer_ContainerIdIn(myContainerIds);
        Map<String, List<OfferEntity>> offersByContainerId = allOffers.stream()
                .collect(Collectors.groupingBy(offer -> offer.getContainer().getContainerId()));

        List<ContainerCargoEntity> allExternalCargos = containerCargoRepository.findExternalCargosByContainerIds(myContainerIds, true);
        Map<String, List<ContainerCargoEntity>> externalCargosByContainerId = allExternalCargos.stream()
                .collect(Collectors.groupingBy(cargo -> cargo.getContainer().getContainerId()));

        List<ContainerStatusDto> dtos = myContainers.stream().map(container -> {
            ContainerStatusDto dto = ContainerStatusDto.fromEntity(container);
            List<OfferEntity> offers = offersByContainerId.getOrDefault(container.getContainerId(), new ArrayList<>());
            
            double confirmedCbmFromOffers = offers.stream()
                    .filter(o -> o.getStatus() == OfferStatus.ACCEPTED || o.getStatus() == OfferStatus.CONFIRMED || o.getStatus() == OfferStatus.SHIPPED || o.getStatus() == OfferStatus.COMPLETED)
                    .mapToDouble(o -> o.getRequest().getCargo().getTotalCbm())
                    .sum();
            
            double resaleCbm = offers.stream().filter(o -> o.getStatus() == OfferStatus.FOR_SALE).mapToDouble(o -> o.getRequest().getCargo().getTotalCbm()).sum();
            double biddingCbm = offers.stream()
                    .filter(o -> o.getStatus() == OfferStatus.PENDING && o.getRequest().getDeadline().isAfter(now))
                    .mapToDouble(o -> o.getRequest().getCargo().getTotalCbm())
                    .sum();
            
            List<ContainerCargoEntity> externalCargos = externalCargosByContainerId.getOrDefault(container.getContainerId(), new ArrayList<>());
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

    public List<CargoDetailDto> getDetailsForContainerStatus(String containerId, String statusString, String currentUserId) {
        UserEntity forwarder = userRepository.findByUserId(currentUserId);
        OfferStatus status = OfferStatus.valueOf(statusString.toUpperCase());
        List<CargoDetailDto> details = new ArrayList<>();

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
    
    @Transactional
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
    
    @Transactional
    public void deleteContainer(String containerId, String currentUserId) {
        ContainerEntity container = containerRepository.findById(containerId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 컨테이너입니다."));
        if (!container.getForwarder().getUserId().equals(currentUserId)) {
            throw new SecurityException("삭제 권한이 없습니다.");
        }

        List<OfferEntity> associatedOffers = offerRepository.findAllByContainer(container);
        List<ContainerCargoEntity> associatedCargos = containerCargoRepository.findAllByContainer(container);

        for (OfferEntity offer : associatedOffers) {
            requestRepository.findBySourceOffer(offer)
                .ifPresent(request -> request.setSourceOffer(null));
        }
        
        if (!associatedOffers.isEmpty()) {
            offerRepository.deleteAll(associatedOffers);
        }
        if (!associatedCargos.isEmpty()) {
            containerCargoRepository.deleteAll(associatedCargos);
        }

        containerRepository.delete(container);
    }
    
    @Transactional
    public void confirmContainer(String containerId, String currentUserId, String imoNumber) {
        ContainerEntity container = containerRepository.findById(containerId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 컨테이너입니다."));
        
        if (!container.getForwarder().getUserId().equals(currentUserId)) {
            throw new SecurityException("확정 권한이 없습니다.");
        }
        
        List<OfferEntity> offers = offerRepository.findAllByContainer(container);
        boolean hasPendingOrForSale = offers.stream()
                .anyMatch(o -> o.getStatus() == OfferStatus.PENDING || o.getStatus() == OfferStatus.FOR_SALE);
        if (hasPendingOrForSale) {
            throw new IllegalStateException("재판매중이거나 입찰중인 화물이 있어 확정할 수 없습니다.");
        }
        
        container.setStatus(ContainerStatus.CONFIRMED);
        container.setImoNumber(imoNumber);

        for (OfferEntity offer : offers) {
            if (offer.getStatus() == OfferStatus.ACCEPTED) {
                offer.setStatus(OfferStatus.CONFIRMED);
            }
        }
        
        eventPublisher.publishEvent(new NotificationEvents.ContainerStatusChangedEvent(this, container, "컨테이너가 확정되었습니다."));
    }
    
    @Transactional
    public void shipContainer(String containerId, String currentUserId) {
        ContainerEntity container = findAndValidateContainer(containerId, currentUserId);
        if (container.getStatus() != ContainerStatus.CONFIRMED) {
            throw new IllegalStateException("'확정' 상태의 컨테이너만 선적할 수 있습니다.");
        }
        container.setStatus(ContainerStatus.SHIPPED);
        
        List<OfferEntity> offersToUpdate = offerRepository.findAllByContainer(container);
        for (OfferEntity offer : offersToUpdate) {
            if (offer.getStatus() == OfferStatus.CONFIRMED) {
                offer.setStatus(OfferStatus.SHIPPED);
            }
        }
        
        eventPublisher.publishEvent(new NotificationEvents.ContainerStatusChangedEvent(this, container, "선적이 완료되었습니다."));
    }

    @Transactional
    public void completeShipment(String containerId, String currentUserId) {
        ContainerEntity container = findAndValidateContainer(containerId, currentUserId);
        if (container.getStatus() != ContainerStatus.SHIPPED) {
            throw new IllegalStateException("'선적완료' 상태의 컨테이너만 운송완료 처리할 수 있습니다.");
        }
        container.setStatus(ContainerStatus.COMPLETED);
        
        List<OfferEntity> offersToUpdate = offerRepository.findAllByContainer(container);
        for (OfferEntity offer : offersToUpdate) {
            if (offer.getStatus() == OfferStatus.SHIPPED) {
                offer.setStatus(OfferStatus.COMPLETED);
            }
        }
        eventPublisher.publishEvent(new NotificationEvents.ContainerStatusChangedEvent(this, container, "운송이 완료되었습니다."));
    }

    private ContainerEntity findAndValidateContainer(String containerId, String currentUserId) {
        ContainerEntity container = containerRepository.findById(containerId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 컨테이너입니다."));
        if (!container.getForwarder().getUserId().equals(currentUserId)) {
            throw new SecurityException("권한이 없습니다.");
        }
        return container;
    }
    
    @Transactional
    public void settleContainer(String containerId, String currentUserId) {
        ContainerEntity container = findAndValidateContainer(containerId, currentUserId);
        if (container.getStatus() != ContainerStatus.COMPLETED) {
            throw new IllegalStateException("'운송완료' 상태의 컨테이너만 정산할 수 있습니다.");
        }
        container.setStatus(ContainerStatus.SETTLED);
        eventPublisher.publishEvent(new NotificationEvents.ContainerStatusChangedEvent(this, container, "정산이 완료되었습니다."));
        chatService.closeChatRoomsForSettledContainer(container);
    }
}