package net.dima.project.repository;

import net.dima.project.entity.Notification;
import net.dima.project.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying; // [✅ import 추가]
import org.springframework.data.jpa.repository.Query;  

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 특정 사용자의 모든 알림을 최신순으로 조회
    List<Notification> findByReceiverAndIsReadFalseOrderByCreatedAtDesc(UserEntity receiver);

    // 특정 사용자의 읽지 않은 알림 개수 조회
    long countByReceiverAndIsReadFalse(UserEntity receiver);
    
    // [✅ 아래 메서드를 추가해주세요]
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.receiver = :receiver")
    void markAllAsReadByUser(UserEntity receiver);
}