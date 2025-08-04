package net.dima.project.dto;

import java.time.LocalDateTime;
import org.springframework.web.multipart.MultipartFile;
import lombok.*;
import net.dima.project.entity.UserEntity;

/**
 * 데이터 전송 객체 (Data Transfer Object)
 * Controller, Service, View 사이에서 데이터를 주고받을 때 사용하는 클래스.
 * Entity와 다르게 DB와 직접적인 관련이 없고, 비즈니스 로직에 필요한 데이터만 담는다.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserDTO {
	private int userSeq;
	private String provider;
	private String providerId;
	private String email;
	private String userId;
	private String userPwd;
	private String userName;
	private String companyName;
    private String phoneNum;
	private String roles;
    private String approvalStatus; // [추가]
	private LocalDateTime createDate;
    private String businessNum;
    private String businessLicenseOrigName;
    private String businessLicenseSavedName;
	
    // 회원가입 폼에서 넘어온 실제 첨부파일을 담는 필드
    private MultipartFile businessLicenseFile;
    
    /**
     * Entity 객체를 DTO 객체로 변환하는 정적 메서드
     * @param userEntity 데이터베이스에서 조회한 Entity 객체
     * @return View나 다른 계층으로 전달될 DTO 객체
     */
	public static UserDTO toDTO(UserEntity userEntity) {
	    return UserDTO.builder()
	            .userSeq(userEntity.getUserSeq())
	            .provider(userEntity.getProvider())
	            .providerId(userEntity.getProviderId())
	            .email(userEntity.getEmail())
	            .userId(userEntity.getUserId())
	            .userPwd(userEntity.getUserPwd())
	            .userName(userEntity.getUserName())
	            .companyName(userEntity.getCompanyName())
	            .phoneNum(userEntity.getPhoneNum())
                .businessNum(userEntity.getBusinessNum())
                .businessLicenseOrigName(userEntity.getBusinessLicenseOrigName())
                .businessLicenseSavedName(userEntity.getBusinessLicenseSavedName())
	            .roles(userEntity.getRoles())
	            .approvalStatus(userEntity.getApprovalStatus()) 
	            .createDate(userEntity.getCreateDate())
	            .build();
	}
}