// [✅ /repository/OfferRepository.java 파일 전체를 이 최종 코드로 교체해주세요]
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
	long countByRequest(RequestEntity request);

    @Query("SELECT o FROM OfferEntity o JOIN FETCH o.request r JOIN FETCH r.cargo c JOIN FETCH o.container WHERE o.forwarder = :forwarder ORDER BY o.createdAt DESC")
    List<OfferEntity> findByForwarderWithDetails(@Param("forwarder") UserEntity forwarder);

    @Query("SELECT o FROM OfferEntity o JOIN FETCH o.request r JOIN FETCH r.cargo c JOIN FETCH o.container " +
           "WHERE o.container.containerId IN :containerIds")
    List<OfferEntity> findOffersForContainersWithDetailsByIds(@Param("containerIds") List<String> containerIds);
    
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
    
    @Query("SELECT o FROM OfferEntity o " +
           "JOIN FETCH o.request r " +
           "JOIN FETCH r.cargo c " +
           "JOIN FETCH r.requester u " +
           "JOIN FETCH o.container " +
           "WHERE o.offerId = :offerId")
    Optional<OfferEntity> findByIdWithDetails(@Param("offerId") Long offerId);

    List<OfferEntity> findAllByRequest(RequestEntity request);

    @Query("SELECT o.request.id FROM OfferEntity o WHERE o.forwarder.userId = :userId")
    Set<Long> findOfferedRequestIdsByUserId(@Param("userId") String userId);
    
    boolean existsByRequestAndForwarder(RequestEntity request, UserEntity forwarder);
    
    long countByContainer(ContainerEntity container);

    List<OfferEntity> findAllByContainer(ContainerEntity container);
    
    /**
     * [✅ 핵심 수정] 특정 요청(Request)에 대해 '낙찰된' 제안을 찾는 쿼리입니다.
     * PENDING 또는 REJECTED가 아닌 제안은 단 하나만 존재해야 합니다.
     * 이 쿼리가 두 개 이상의 결과를 반환하면 NonUniqueResultException이 발생합니다.
     */
    @Query("SELECT o FROM OfferEntity o JOIN FETCH o.forwarder f WHERE o.request = :request AND o.status NOT IN ('PENDING', 'REJECTED')")
    Optional<OfferEntity> findWinningOfferForRequest(@Param("request") RequestEntity request);
    

    long countByContainerAndStatusIn(ContainerEntity container, List<OfferStatus> statuses);
    
    List<OfferEntity> findByForwarderAndStatusIn(UserEntity forwarder, List<OfferStatus> statuses);
    
    @Query("SELECT o FROM OfferEntity o " +
    	       "JOIN FETCH o.request r " +
    	       "JOIN FETCH r.cargo c " +
    	       "WHERE o.container.containerId IN :containerIds")
    	List<OfferEntity> findAllByContainer_ContainerIdIn(@Param("containerIds") List<String> containerIds);
    
    
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
    
    @Query("SELECT o FROM OfferEntity o JOIN FETCH o.request r JOIN FETCH r.cargo c WHERE o.container.status = :status")
    List<OfferEntity> findAllByContainerStatus(@Param("status") ContainerStatus status);
    
    long countByStatusAndCreatedAtBetween(OfferStatus status, LocalDateTime start, LocalDateTime end);

    long countByForwarder(UserEntity forwarder);

    long countByForwarderAndStatusIn(UserEntity forwarder, List<OfferStatus> statuses);
    
    @Query("SELECT o.request.requestId, COUNT(o) FROM OfferEntity o WHERE o.request IN :requests GROUP BY o.request.requestId")
    List<Object[]> countOffersByRequestIn(@Param("requests") List<RequestEntity> requests);

    /**
     * [✅ 핵심 수정] 여러 요청에 대한 '낙찰된' 제안 정보를 한 번의 쿼리로 조회합니다. N+1 문제 해결의 핵심입니다.
     * findWinningOfferForRequest와 동일한 로직을 사용합니다.
     */
    @Query("SELECT o FROM OfferEntity o JOIN FETCH o.forwarder f " +
           "WHERE o.request IN :requests AND o.status NOT IN ('PENDING', 'REJECTED')")
    List<OfferEntity> findWinningOffersForRequests(@Param("requests") List<RequestEntity> requests);
    
    @Query("SELECT o.request.id FROM OfferEntity o WHERE o.forwarder.userId = :userId AND o.request IN :requests")
    Set<Long> findOfferedRequestIdsByUserIdAndRequestIn(@Param("userId") String userId, @Param("requests") List<RequestEntity> requests);

}