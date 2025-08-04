package net.dima.project.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.dima.project.entity.OfferStatus;
import jakarta.persistence.EnumType;     // import 추가
import jakarta.persistence.Enumerated; // import 추가

import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Entity
@Table(name = "offer")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfferEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long offerId;

    // [✅ 이 부분을 아래와 같이 수정하세요]
    @ManyToOne(fetch = FetchType.LAZY) // cascade 속성을 완전히 제거합니다.
    @JoinColumn(name = "request_id", nullable = false)
    private RequestEntity request;
	
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "container_id", nullable = false)
	private ContainerEntity container;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "forwarder_id", nullable = false)
	private UserEntity forwarder;

	@Column(nullable = false)
	private BigDecimal price;

	@Column(nullable = false)
	private String currency;

    @Enumerated(EnumType.STRING) // [✅ 1. 어노테이션 추가]
    @Column(nullable = false)
    private OfferStatus status;

	@CreationTimestamp
	@Column(name = "created_at", updatable = false)
	private LocalDateTime createdAt;
}