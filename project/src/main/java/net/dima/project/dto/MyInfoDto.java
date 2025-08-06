package net.dima.project.dto;

import lombok.Builder;
import lombok.Data;
import net.dima.project.entity.UserEntity;
import java.time.format.DateTimeFormatter;

@Data
@Builder
public class MyInfoDto {
    // 기본 정보
    private String userId;
    private String userName;
    private String email;
    private String provider;
    private String createDate;

    // 상세 정보
    private String companyName;
    private String phoneNum;
    private String businessNum;
    private String approvalStatus; // 포워더인 경우에만 의미 있음

    // Entity를 DTO로 변환하는 정적 메서드
    public static MyInfoDto from(UserEntity user) {
        return MyInfoDto.builder()
                .userId(user.getUserId())
                .userName(user.getUserName())
                .email(user.getEmail())
                .provider(user.getProvider())
                .createDate(user.getCreateDate().format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일")))
                .companyName(user.getCompanyName())
                .phoneNum(user.getPhoneNum())
                .businessNum(user.getBusinessNum())
                .approvalStatus(user.getApprovalStatus())
                .build();
    }
}