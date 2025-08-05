package net.dima.project.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_seq", nullable = false)
    private UserEntity receiver; // 알림을 받는 사용자

    @Column(nullable = false, length = 255)
    private String message; // 알림 메시지

    @Column(length = 255)
    private String url; // 클릭 시 이동할 URL

    @Column(nullable = false)
    private boolean isRead; // 읽음 여부

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt; // 생성 시간
}