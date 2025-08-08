package net.dima.project.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import net.dima.project.entity.ContainerEntity;
import net.dima.project.entity.ContainerStatus;
import net.dima.project.entity.OfferEntity;
import net.dima.project.entity.OfferStatus;
import net.dima.project.entity.RequestEntity;
import net.dima.project.entity.UserEntity;

@Repository
public interface OfferRepository extends JpaRepository<OfferEntity, Long>, JpaSpecificationExecutor<OfferEntity> {
	// 나의요청조회탭을 만들기 위해 만들어짐.
	long countByRequest(RequestEntity request);

    /**
     * 나의제안조회 페이지 목록을 위한 쿼리
     */
    @Query("SELECT o FROM OfferEntity o JOIN FETCH o.request r JOIN FETCH r.cargo c JOIN FETCH o.container WHERE o.forwarder = :forwarder ORDER BY o.createdAt DESC")
    List<OfferEntity> findByForwarderWithDetails(@Param("forwarder") UserEntity forwarder);

    /**
     * 컨테이너조회 페이지의 CBM 계산을 위한 쿼리
     */
    @Query("SELECT o FROM OfferEntity o JOIN FETCH o.request r JOIN FETCH r.cargo c JOIN FETCH o.container " +
           "WHERE o.container.containerId IN :containerIds")
    List<OfferEntity> findOffersForContainersWithDetailsByIds(@Param("containerIds") List<String> containerIds);
    
    /**
     * 컨테이너조회 상세보기 기능을 위한 쿼리
     */
    @Query("SELECT o FROM OfferEntity o " +
            "JOIN FETCH o.request r " +
            "JOIN FETCH r.cargo c " +
            "WHERE o.container.containerId = :containerId " +
            "AND o.status IN :statuses " +
            "AND o.forwarder = :forwarder")
     List<OfferEntity> findDetailsByContainerAndStatusInWithAllDetails(
             @Param("containerId") String containerId,
             @Param("statuses") List<OfferStatus> statuses,
             @Param("forwarder") UserEntity forwarder);
    
    /**
     * 나의제안조회 상세보기 기능을 위한 쿼리
     */
    @Query("SELECT o FROM OfferEntity o " +
           "JOIN FETCH o.request r " +
           "JOIN FETCH r.cargo c " +
           "JOIN FETCH r.requester u " +
           "JOIN FETCH o.container " +
           "WHERE o.offerId = :offerId")
    Optional<OfferEntity> findByIdWithDetails(@Param("offerId") Long offerId);

    /**
     * ResaleService에서 재판매 취소 시, 해당 요청에 달린 입찰들을 찾기 위한 메서드
     */
    List<OfferEntity> findAllByRequest(RequestEntity request);

    // [✅ 추가] 특정 사용자가 제안한 모든 요청(Request)의 ID 목록을 조회하는 쿼리
    @Query("SELECT o.request.id FROM OfferEntity o WHERE o.forwarder.userId = :userId")
    Set<Long> findOfferedRequestIdsByUserId(@Param("userId") String userId);
    
    
    // [✅ 추가] 특정 request와 forwarder로 제안이 존재하는지 확인
    boolean existsByRequestAndForwarder(RequestEntity request, UserEntity forwarder);
    
    // [✅ 추가] 특정 컨테이너에 연결된 제안(Offer)의 개수를 세는 메서드
    long countByContainer(ContainerEntity container);

    // [✅ 추가] 특정 컨테이너에 연결된 모든 제안(Offer) 목록을 조회하는 메서드
    List<OfferEntity> findAllByContainer(ContainerEntity container);
    
    // CLOSED된 요청에서 낙찰된 제안(ACCEPTED 상태)을찾는 메서드
    // [✅ 수정] findTopByRequestAndStatus 메서드를 아래 코드로 교체합니다.
    @Query("SELECT o FROM OfferEntity o JOIN FETCH o.forwarder f WHERE o.request = :request AND o.status IN ('ACCEPTED', 'CONFIRMED', 'RESOLD', 'SHIPPED', 'COMPLETED')")
    Optional<OfferEntity> findWinningOfferForRequest(@Param("request") RequestEntity request);
    

    // [✅ 추가] 특정 컨테이너와 연결된 제안 중, 주어진 상태 목록에 해당하는 제안들의 개수를 세는 메서드
    long countByContainerAndStatusIn(ContainerEntity container, List<OfferStatus> statuses);
    
    // [✅ 추가] 특정 사용자가 제안자이고, 주어진 상태 목록에 포함되는 제안들 조회
    List<OfferEntity> findByForwarderAndStatusIn(UserEntity forwarder, List<OfferStatus> statuses);
    
    @Query("SELECT o FROM OfferEntity o " +
    	       "JOIN FETCH o.request r " +
    	       "JOIN FETCH r.cargo c " +
    	       "WHERE o.container.containerId IN :containerIds")
    	List<OfferEntity> findAllByContainer_ContainerIdIn(@Param("containerIds") List<String> containerIds);
    
    
 // OfferRepository 인터페이스에 아래 메서드 선언을 추가하세요.
    @Query("SELECT o FROM OfferEntity o " +
           "JOIN FETCH o.request r " +
           "JOIN FETCH r.cargo c " +
           "WHERE o.container.containerId = :containerId " +
           "AND o.status = :status " +
           "AND o.forwarder = :forwarder")
    List<OfferEntity> findDetailsByContainerAndStatusWithAllDetails(
            @Param("containerId") String containerId,
            @Param("status") OfferStatus status,
            @Param("forwarder") UserEntity forwarder);
    
 // ... 기존 코드 ...
    @Query("SELECT o FROM OfferEntity o JOIN FETCH o.request r JOIN FETCH r.cargo c WHERE o.container.status = :status")
    List<OfferEntity> findAllByContainerStatus(@Param("status") ContainerStatus status);
    
    long countByStatusAndCreatedAtBetween(OfferStatus status, LocalDateTime start, LocalDateTime end);

    
    long countByForwarder(UserEntity forwarder); // [추가]

    // [추가] 낙찰 성공 건수 (재판매 포함)
    long countByForwarderAndStatusIn(UserEntity forwarder, List<OfferStatus> statuses);
    
    
    
    
    /**
     * [✅ 추가] 여러 요청(Request)에 대한 입찰(Offer) 개수를 한 번의 쿼리로 조회하여 Map으로 반환합니다.
     * @param requests 입찰 수를 조회할 요청 엔티티 목록
     * @return Map<Long, Long> key: requestId, value: offer count
     */
    @Query("SELECT o.request.requestId, COUNT(o) FROM OfferEntity o WHERE o.request IN :requests GROUP BY o.request.requestId")
    List<Object[]> countOffersByRequestIn(@Param("requests") List<RequestEntity> requests);

    
    
    /**
     * [✅ 수정] 여러 요청에 대한 '낙찰된' 제안 정보를 한 번의 쿼리로 조회합니다. N+1 문제 해결의 핵심입니다.
     * @param requests 낙찰자를 조회할 요청 엔티티 목록
     * @return 낙찰된 제안(Offer) 목록
     */
    @Query("SELECT o FROM OfferEntity o JOIN FETCH o.forwarder f " +
           "WHERE o.request IN :requests AND o.status IN ('ACCEPTED', 'CONFIRMED', 'RESOLD', 'SHIPPED', 'COMPLETED')")
    List<OfferEntity> findWinningOffersForRequests(@Param("requests") List<RequestEntity> requests);
    
    /**
     * [✅ 추가] 특정 사용자가 주어진 여러 요청(Request) 목록 중 어떤 것들에 제안했는지 ID 목록을 반환합니다.
     * @param userId 현재 사용자의 ID
     * @param requests 제안 여부를 확인할 요청 엔티티 목록
     * @return Set<Long> 사용자가 제안한 요청(Request)의 ID들
     */
    @Query("SELECT o.request.id FROM OfferEntity o WHERE o.forwarder.userId = :userId AND o.request IN :requests")
    Set<Long> findOfferedRequestIdsByUserIdAndRequestIn(@Param("userId") String userId, @Param("requests") List<RequestEntity> requests);

}