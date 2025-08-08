package net.dima.project.repository;

import net.dima.project.entity.OfferEntity;
import net.dima.project.entity.RequestEntity;
import net.dima.project.entity.RequestStatus;
import net.dima.project.entity.UserEntity;
import org.springframework.data.domain.Sort; // [✅ import 추가]

import org.springframework.data.jpa.repository.JpaSpecificationExecutor; 
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
// @Param은 더 이상 필요 없으므로 import 문을 지워도 됩니다.
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;    // [✅ import 추가]
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RequestRepository extends JpaRepository<RequestEntity, Long>, JpaSpecificationExecutor<RequestEntity> {

    // 이름 기반(':status') -> 인덱스 기반('?1')으로 수정
    @Query("SELECT r FROM RequestEntity r JOIN FETCH r.cargo c JOIN FETCH r.requester u WHERE r.status = ?1 ORDER BY r.deadline ASC")
    List<RequestEntity> findOpenRequestsWithDetails(RequestStatus status);

    // [수정] requester와 sourceOffer 정보를 함께 불러오도록 JOIN FETCH 추가
    @Query("SELECT r FROM RequestEntity r " +
           "LEFT JOIN FETCH r.cargo c " +
           "LEFT JOIN FETCH r.requester u " +
           "LEFT JOIN FETCH r.sourceOffer so " +
           "WHERE r.requestId = ?1")
    Optional<RequestEntity> findRequestWithDetailsById(Long requestId);
    
    // [✅ 추가] sourceOffer를 기준으로 Request를 찾는 메서드 // 재판매 요청을 찾는 매서드임 재판매한거를 컨테이너탭에서 취소하기 위함.
    Optional<RequestEntity> findBySourceOffer(OfferEntity sourceOffer);
    
    // [✅ 추가] 요청자(requester)와 상태(status)로 요청 목록을 찾는 메서드
    List<RequestEntity> findByRequesterAndStatus(UserEntity requester, RequestStatus status);
    
    // 낙찰자 정보를 찾아 DTO에 담음.
    @Query("SELECT r FROM RequestEntity r JOIN FETCH r.cargo c WHERE r.requester = ?1 AND r.status IN ('OPEN', 'CLOSED') ORDER BY r.deadline DESC")
    List<RequestEntity> findActiveAndClosedRequestsByRequester(UserEntity requester);

    // [✅ 추가] 특정 사용자가 요청자이고, 상태가 CLOSED이며, 재판매 요청인 건들 조회
    List<RequestEntity> findByRequesterAndStatusAndSourceOfferIsNotNull(UserEntity requester, RequestStatus status);
 // RequestRepository.java 에 추가
    List<RequestEntity> findByRequesterAndSourceOfferIsNull(UserEntity requester);
    // [✅ 이 메서드를 추가해주세요]
    List<RequestEntity> findAllByRequesterAndSourceOfferIsNotNull(UserEntity requester, Sort sort);
    
    // [✅ 이 메서드를 새로 추가하거나, 위 Page 반환 메서드를 이걸로 교체해주세요]
    List<RequestEntity> findByRequesterAndSourceOfferIsNull(UserEntity requester, Sort sort);
    
    // [✅ 이 메서드를 인터페이스 안에 추가해주세요]
    Page<RequestEntity> findByRequesterAndSourceOfferIsNull(UserEntity requester, Pageable pageable);
    
    // [✅ 이 메서드가 있는지 확인해주세요]
    List<RequestEntity> findByRequesterAndSourceOfferIsNullAndStatus(UserEntity requester, RequestStatus status);
 // [✅ 이 메서드를 추가해주세요]
    List<RequestEntity> findBySourceOfferIn(List<OfferEntity> sourceOffers);

    /**
     * 재판매의 근원이 되는 제안(Offer)으로 요청(Request)을 찾습니다.
     * 중복 데이터가 있을 경우를 대비해 최신순으로 정렬된 리스트를 반환합니다.
     */
    
    @Query("SELECT r FROM RequestEntity r WHERE r.sourceOffer = :sourceOffer ORDER BY r.createdAt DESC")
    List<RequestEntity> findBySourceOfferOrderedByCreatedAtDesc(@Param("sourceOffer") OfferEntity sourceOffer);
    
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    // [추가] 마감일이 임박했는데도 입찰이 없는 요청 수를 세는 메서드
    @Query("SELECT COUNT(r) FROM RequestEntity r WHERE r.status = 'OPEN' AND r.deadline < :deadlineThreshold AND NOT EXISTS (SELECT o FROM OfferEntity o WHERE o.request = r)")
    long countOpenRequestsWithNoBids(@Param("deadlineThreshold") LocalDateTime deadlineThreshold);
    
    // [추가] 특정 사용자가 올린 모든 요청 수를 세는 메서드
    long countByRequester(UserEntity requester);

    // [추가] 특정 사용자의 요청 중 특정 상태인 건의 수를 세는 메서드
    long countByRequesterAndStatus(UserEntity requester, RequestStatus status);

    // [추가] 특정 사용자가 요청한 모든 화물의 CBM 총합을 구하는 메서드
    @Query("SELECT COALESCE(SUM(c.totalCbm), 0) FROM RequestEntity r JOIN r.cargo c WHERE r.requester = :requester")
    double sumTotalCbmByRequester(@Param("requester") UserEntity requester);
    
    
    /**
     * 입찰이 1건 이상 있었지만, 화주가 확정하지 않고 마감된 요청 수를 계산합니다.
     * (상태: OPEN, 마감일: 지남, 입찰 수: 1 이상)
     */
    @Query("SELECT COUNT(r) FROM RequestEntity r WHERE r.status = 'OPEN' AND r.deadline < :now AND EXISTS (SELECT o FROM OfferEntity o WHERE o.request = r)")
    long countOpenRequestsWithBidsPastDeadline(@Param("now") LocalDateTime now);

    /**
     * 전체 마감된 요청 수를 계산합니다. (비율 계산의 분모로 사용)
     * (상태: CLOSED 또는 (상태: OPEN 이고 마감일 지남))
     */
    @Query("SELECT COUNT(r) FROM RequestEntity r WHERE r.status = 'CLOSED' OR (r.status = 'OPEN' AND r.deadline < :now)")
    long countTotalClosedOrExpiredRequests(@Param("now") LocalDateTime now);
    
    /**
     * [✅ 핵심 추가] 스케줄러가 마감된 재판매 요청을 찾기 위한 쿼리입니다.
     * - sourceOffer가 NULL이 아니고 (재판매 요청이고)
     * - status가 'OPEN'이며 (아직 처리되지 않았고)
     * - deadline이 현재 시간 이전인 (마감 시간이 지난)
     * 모든 요청을 조회합니다.
     */
    @Query("SELECT r FROM RequestEntity r WHERE r.sourceOffer IS NOT NULL AND r.status = 'OPEN' AND r.deadline < :now")
    List<RequestEntity> findExpiredResaleRequests(@Param("now") LocalDateTime now);

}