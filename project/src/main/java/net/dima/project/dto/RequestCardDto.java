package net.dima.project.dto;

import lombok.Builder;
import lombok.Data;
import net.dima.project.entity.RequestEntity;
import net.dima.project.entity.RequestStatus; // [✅ 추가]

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
@Builder
public class RequestCardDto {
    private Long id;
    private String idLabel;
    private String itemName;
    private String incoterms;
    private String departurePort;
    private String arrivalPort;
    private String registrationDate; // [✅ 1. 이 필드를 추가하세요]
    private String deadline;
    private String desiredArrivalDate;
    private LocalDate desiredArrivalDateAsLocalDate; 
    private String tradeType;
    private String transportType;
    private Double cbm;
    private LocalDateTime deadlineDateTime;
    private String requesterId;
    private boolean hasMyOffer; // [✅ 추가]
    private RequestStatus status; 

    // Entity를 DTO로 변환하는 정적 메서드
    public static RequestCardDto fromEntity(RequestEntity entity, boolean hasMyOffer) {
        String idLabel = entity.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + " No." + String.format("%03d", entity.getRequestId());

        return RequestCardDto.builder()
                .id(entity.getRequestId())
                .idLabel(idLabel)
                .itemName(entity.getCargo().getItemName()) // cargo 엔티티에서 itemName 가져오기
                .incoterms(entity.getCargo().getIncoterms()) // cargo 엔티티에서 incoterms 가져오기
                .departurePort(entity.getDeparturePort())
                .arrivalPort(entity.getArrivalPort())
                .registrationDate(entity.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy.MM.dd")))
                .deadline(entity.getDeadline().format(DateTimeFormatter.ofPattern("yyyy.MM.dd")))
                .desiredArrivalDate(entity.getDesiredArrivalDate() != null 
                ? entity.getDesiredArrivalDate().format(DateTimeFormatter.ofPattern("yyyy.MM.dd")) 
                : "미지정")
                .desiredArrivalDateAsLocalDate(entity.getDesiredArrivalDate())
                .tradeType(entity.getTradeType())
                .transportType(entity.getTransportType())
                .cbm(entity.getCargo().getTotalCbm()) // cargo 엔티티에서 totalCbm 가져오기
                .deadlineDateTime(entity.getDeadline())
                .requesterId(entity.getRequester().getUserId())
                .hasMyOffer(hasMyOffer)
                .status(entity.getStatus()) // [✅ 추가] status 값 설정
                .build();
    }
    
    // 기존 fromEntity는 혹시 모르니 남겨두거나, 위 메서드에 맞춰 수정
    public static RequestCardDto fromEntity(RequestEntity entity) {
        return fromEntity(entity, false); // 기본값은 false
    }
    
    
}