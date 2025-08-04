package net.dima.project.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import jakarta.persistence.EnumType;     // import 추가
import jakarta.persistence.Enumerated; // import 추가

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name="request")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long requestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cargo_id", nullable = false)
    private CargoEntity cargo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private UserEntity requester;

    @Column(nullable = false)
    private String departurePort;

    @Column(nullable = false)
    private String arrivalPort;

    @Column(nullable = false)
    private LocalDateTime deadline;
    
    @Column(name = "desired_arrival_date") // [✅ 이 필드를 추가해주세요]
    private LocalDate desiredArrivalDate;

    @Column(nullable = false)
    private String tradeType;

    @Column(nullable = false)
    private String transportType;

    @Enumerated(EnumType.STRING) // [✅ 1. 어노테이션 추가]
    @Column(nullable = false)
    private RequestStatus status; 

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_offer_id")
    private OfferEntity sourceOffer;

    // 재판매 요청의 근원을 추적하기 위한 컬럼. 지금 당장은 사용하지 않지만 구조를 위해 추가합니다.
    // private Long sourceOfferId;
}