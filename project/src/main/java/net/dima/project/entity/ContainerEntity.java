package net.dima.project.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "container")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContainerEntity {

    @Id
    private String containerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "forwarder_id", nullable = false)
    private UserEntity forwarder;

    @Column(nullable = false)
    private String departurePort;

    @Column(nullable = false)
    private String arrivalPort;

    @Column(nullable = false)
    private LocalDate etd; // Estimated Time of Departure

    @Column(nullable = false)
    private LocalDate eta; // Estimated Time of Arrival

    @Column(nullable = false)
    private String size;

    @Column(name = "capacity_cbm", nullable = false)
    private Double capacityCbm;
    
 // ... ContainerEntity 클래스 안에 아래 필드를 추가해주세요 ...
    @Column(name = "imo_number")
    private String imoNumber;

    @Enumerated(EnumType.STRING) // [✅ 수정] Enum 타입을 DB에 문자열로 저장
    @Column(nullable = false)
    private ContainerStatus status; // [✅ 수정] String -> ContainerStatus
    
}