package net.dima.project.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import net.dima.project.entity.UserEntity;

/**
 * 'users' 테이블에 대한 데이터베이스 작업을 처리하는 Spring Data JPA 리포지토리
 */
public interface UserRepository extends JpaRepository<UserEntity, Integer> {
    
    // 아이디로 사용자 정보를 조회 (중복 확인 및 로그인 시 사용)
    UserEntity findByUserId(String userId);

    // 이메일로 사용자 정보를 조회 (중복 확인 시 사용)
    UserEntity findByEmail(String email);

    // 소셜 로그인 제공자와 고유 ID로 사용자 정보를 조회 (카카오 로그인 시 사용)
    UserEntity findByProviderAndProviderId(String provider, String providerId);
    
    // [수정] 여러 역할을 조회할 수 있도록 In(List<String> roles)으로 변경
    List<UserEntity> findByRolesIn(List<String> roles);

    long countByRoles(String roles);
}