package net.dima.project.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class BlDto {
    // B/L 고유 정보
    private String blNo;
    private LocalDate issueDate;

    // 화주 정보
    private String shipperName;
    private String shipperAddress; // 현재는 null로 처리

    // 수하인 정보
    private String consigneeName;
    private String consigneeAddress; // 현재는 null로 처리

    // 포워더 정보
    private String forwarderName;

    // 운송 정보
    private String vesselName; // 현재는 null로 처리
    private String imoNumber;
    private String portOfLoading;
    private String portOfDischarge;

    // 화물 및 컨테이너 정보
    private String containerNo;
    private String descriptionOfGoods;
    private Double cbm;
}