package net.dima.project.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

import java.math.BigDecimal;

@Entity
@Table(name = "container_cargo")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContainerCargoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "container_id", nullable = false)
    private ContainerEntity container;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offer_id") // 외부 거래 시 NULL일 수 있음
    private OfferEntity offer;

    @Column(name = "cbm_loaded", nullable = false)
    private Double cbmLoaded;

    @Column(name = "is_external", nullable = false)
    private Boolean isExternal;

    @Column(name = "external_cargo_name")
    private String externalCargoName;
    
    // [신규] 운임 정보 필드
    @Column(name = "freight_cost")
    private BigDecimal freightCost;

    @Column(name = "freight_currency")
    private String freightCurrency;

    @CreationTimestamp
    @Column(name = "added_at", updatable = false)
    private LocalDateTime addedAt;
}