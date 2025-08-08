package net.dima.project.repository;

import net.dima.project.entity.ContainerCargoEntity;
import net.dima.project.entity.ContainerEntity;
import net.dima.project.entity.OfferEntity;
import net.dima.project.entity.UserEntity;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ContainerCargoRepository extends JpaRepository<ContainerCargoEntity, Long> {

    @Query("SELECT SUM(cc.cbmLoaded) FROM ContainerCargoEntity cc WHERE cc.container.containerId = :containerId")
    Double sumCbmByContainerId(@Param("containerId") String containerId);

    // [삭제] 이 메서드를 삭제합니다.
    // List<ContainerCargoEntity> findByContainer_ContainerIdAndIsExternal(String containerId, boolean isExternal);

    // [신규] @Query를 사용하는 명확한 이름의 메서드를 추가합니다.
    @Query("SELECT cc FROM ContainerCargoEntity cc " +
           "WHERE cc.container.containerId = :containerId " +
           "AND cc.isExternal = :isExternal")
    List<ContainerCargoEntity> findExternalCargosByContainerId(
            @Param("containerId") String containerId,
            @Param("isExternal") boolean isExternal);
    
    List<ContainerCargoEntity> findByContainer_ContainerId(String containerId);
    
    // [✅ 수정] 메서드 이름에서 _(언더스코어)를 제거합니다.
    Optional<ContainerCargoEntity> findByOfferOfferId(Long offerId);
    
    // [✅ 추가] 특정 컨테이너에 연결된 외부 화물의 개수를 세는 메서드
    long countByContainer(ContainerEntity container);
    
    // [✅ 추가] 특정 컨테이너에 연결된 모든 ContainerCargo 목록을 조회
    List<ContainerCargoEntity> findAllByContainer(ContainerEntity container);
    
 // [✅ ContainerCargoRepository.java에 이 메서드를 추가해주세요]
    boolean existsByOffer_OfferId(Long offerId);
    
 // ContainerCargoRepository.java 인터페이스 안에 아래 메소드를 추가해주세요.

    @Query("SELECT cc FROM ContainerCargoEntity cc " +
           "WHERE cc.container.containerId IN :containerIds " +
           "AND cc.isExternal = :isExternal")
    List<ContainerCargoEntity> findExternalCargosByContainerIds(
            @Param("containerIds") List<String> containerIds,
            @Param("isExternal") boolean isExternal);

}