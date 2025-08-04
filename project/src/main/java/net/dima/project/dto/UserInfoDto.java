package net.dima.project.dto;

import lombok.Builder;
import lombok.Data;
import net.dima.project.entity.UserEntity;

import java.time.format.DateTimeFormatter;

@Data
@Builder
public class UserInfoDto {
    // 기본 정보
    private Integer userSeq;
    private String companyName;
    private String userName;
    private String userId;
    private String email;
    private String phoneNum;
    private String createDate;
    private String approvalStatus; // 계정 정지 상태 등을 표시하기 위함

    // 활동 요약
    private long totalRequests;
    private long completedDeals;
    private double totalCbm;

    public static UserInfoDto from(UserEntity user, long totalRequests, long completedDeals, double totalCbm) {
        return UserInfoDto.builder()
                .userSeq(user.getUserSeq())
                .companyName(user.getCompanyName())
                .userName(user.getUserName())
                .userId(user.getUserId())
                .email(user.getEmail())
                .phoneNum(user.getPhoneNum())
                .createDate(user.getCreateDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                .approvalStatus(user.getApprovalStatus())
                .totalRequests(totalRequests)
                .completedDeals(completedDeals)
                .totalCbm(totalCbm)
                .build();
    }
}