package net.dima.project.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.dima.project.dto.UserDTO;

/**
 * 데이터베이스의 'users' 테이블과 직접 매핑되는 클래스 (JPA Entity)
 * 사용자의 모든 정보를 담고 있으며, 데이터베이스 영속성을 관리합니다.
 */
@Entity
@Table(name="users") // 'users' 테이블에 매핑
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class UserEntity {
    @Id
    @Column(name="user_seq")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer userSeq; // 사용자 고유 번호 (PK)

    @Builder.Default
    @Column(name="provider", nullable = false)
    private String provider = "local";  // 가입 방식 (local: 일반, kakao: 카카오)

    @Column(name="provider_id")
    private String providerId; // 소셜 로그인 시 제공되는 고유 ID

    @Column(name="email", nullable = false, unique = true)
    private String email; // 이메일 (로그인 및 식별에 사용)

    @Column(name="user_name", nullable = false)
    private String userName; // 사용자 이름

    @Column(name="company_name")
    private String companyName;
    
    @Column(name="user_id", unique = true)
    private String userId; // 로그인 아이디

    @Column(name="user_pwd")
    private String userPwd; // 비밀번호 (암호화하여 저장)

    @Column(name="phone_num")
    private String phoneNum;
    
    @Column(name="business_num")
    private String businessNum; // 사업자등록번호

    @Column(name="business_license_orig_name")
    private String businessLicenseOrigName; // 사업자등록증 원본 파일명

    @Column(name="business_license_saved_name")
    private String businessLicenseSavedName; // 서버에 저장될 파일명
    
    @Builder.Default
    @Column(name="roles")
    private String roles = "ROLE_USER"; // 사용자 권한 ("ROLE_fwd", "ROLE_cus" 등)
    
    @Column(name="approval_status") // [추가] 승인 상태 필드
    private String approvalStatus;

    @Column(name="create_date", updatable = false) // 생성 시간은 업데이트되지 않도록 설정
    @CreationTimestamp // 데이터 생성 시 자동으로 현재 시간 기록
    private LocalDateTime createDate; // 가입일

    /**
     * DTO(Data Transfer Object)를 Entity로 변환하는 정적 메서드
     * @param userDTO Controller나 Service에서 사용하는 데이터 객체
     * @return 데이터베이스에 저장될 Entity 객체
     */
    public static UserEntity toEntity(UserDTO userDTO) {
        return UserEntity.builder()
                .provider(userDTO.getProvider() != null ? userDTO.getProvider() : "local")
                .providerId(userDTO.getProviderId())
                .email(userDTO.getEmail())
                .userId(userDTO.getUserId())
                .userPwd(userDTO.getUserPwd())
                .userName(userDTO.getUserName())
                .companyName(userDTO.getCompanyName()) // [✅ 이 줄을 추가해주세요]
                .phoneNum(userDTO.getPhoneNum())
                .businessNum(userDTO.getBusinessNum())
                .businessLicenseOrigName(userDTO.getBusinessLicenseOrigName())
                .businessLicenseSavedName(userDTO.getBusinessLicenseSavedName())
                .roles(userDTO.getRoles())
                .approvalStatus(userDTO.getApprovalStatus()) // [추가]
                .build();
    }
}