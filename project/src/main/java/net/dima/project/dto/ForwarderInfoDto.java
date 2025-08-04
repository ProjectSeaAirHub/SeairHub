package net.dima.project.dto;

import lombok.Builder;
import lombok.Data;
import net.dima.project.entity.UserEntity;
import java.time.format.DateTimeFormatter;

@Data
@Builder
public class ForwarderInfoDto {
    // 기본 정보
    private Integer userSeq;
    private String companyName;
    private String userName;
    private String userId;
    private String email;
    private String phoneNum;
    private String createDate;
    private String approvalStatus;
    private String businessLicenseSavedName;

    // 활동 요약
    private long containerCount;
    private long totalOffers;
    private long acceptedOffers;

    public static ForwarderInfoDto from(UserEntity user, long containerCount, long totalOffers, long acceptedOffers) {
        return ForwarderInfoDto.builder()
                .userSeq(user.getUserSeq())
                .companyName(user.getCompanyName())
                .userName(user.getUserName())
                .userId(user.getUserId())
                .email(user.getEmail())
                .phoneNum(user.getPhoneNum())
                .createDate(user.getCreateDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                .approvalStatus(user.getApprovalStatus())
                .businessLicenseSavedName(user.getBusinessLicenseSavedName())
                .containerCount(containerCount)
                .totalOffers(totalOffers)
                .acceptedOffers(acceptedOffers)
                .build();
    }
}