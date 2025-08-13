// [✅ /entity/UserEntity.java 파일 전체를 이 최종 코드로 교체해주세요]
package net.dima.project.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.dima.project.dto.UserDTO;

@Entity
@Table(name="users")
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class UserEntity {
    @Id
    @Column(name="user_seq")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer userSeq;

    @Builder.Default
    @Column(name="provider", nullable = false)
    private String provider = "local";

    @Column(name="provider_id")
    private String providerId;

    @Column(name="email", nullable = false, unique = true)
    private String email;

    @Column(name="user_name", nullable = false)
    private String userName;

    // [✅ 핵심 수정 1] companyName을 필수로 지정합니다.
    @Column(name="company_name", nullable = false)
    private String companyName;
    
    @Column(name="user_id", unique = true)
    private String userId;

    @Column(name="user_pwd")
    private String userPwd;

    @Column(name="phone_num")
    private String phoneNum;
    
    // [✅ 핵심 수정 2] businessNum을 필수로 지정합니다.
    @Column(name="business_num", nullable = false)
    private String businessNum;

    @Column(name="business_license_orig_name")
    private String businessLicenseOrigName;

    @Column(name="business_license_saved_name")
    private String businessLicenseSavedName;
    
    @Builder.Default
    @Column(name="roles")
    private String roles = "ROLE_USER";
    
    @Column(name="approval_status")
    private String approvalStatus;

    @Column(name="create_date", updatable = false)
    @CreationTimestamp
    private LocalDateTime createDate;

    public static UserEntity toEntity(UserDTO userDTO) {
        return UserEntity.builder()
                .provider(userDTO.getProvider() != null ? userDTO.getProvider() : "local")
                .providerId(userDTO.getProviderId())
                .email(userDTO.getEmail())
                .userId(userDTO.getUserId())
                .userPwd(userDTO.getUserPwd())
                .userName(userDTO.getUserName())
                .companyName(userDTO.getCompanyName())
                .phoneNum(userDTO.getPhoneNum())
                .businessNum(userDTO.getBusinessNum())
                .businessLicenseOrigName(userDTO.getBusinessLicenseOrigName())
                .businessLicenseSavedName(userDTO.getBusinessLicenseSavedName())
                .roles(userDTO.getRoles())
                .approvalStatus(userDTO.getApprovalStatus())
                .build();
    }
}