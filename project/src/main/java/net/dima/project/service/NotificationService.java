// [✅ NotificationService.java 파일 전체를 이 최종 코드로 교체해주세요]
package net.dima.project.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dima.project.dto.NotificationDto;
import net.dima.project.entity.Notification;
import net.dima.project.entity.UserEntity;
import net.dima.project.repository.NotificationRepository;
import net.dima.project.repository.UserRepository;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SseEmitterService sseEmitterService; // SseEmitterService 주입

    /**
     * 알림을 DB에 저장하고, 트랜잭션이 성공적으로 완료된 후에만 SSE 이벤트를 전송합니다.
     */
    @Transactional
    public void sendNotification(UserEntity receiver, String message, String url) {
        Notification notification = Notification.builder()
                .receiver(receiver)
                .message(message)
                .url(url)
                .isRead(false)
                .build();
        
        notificationRepository.save(notification);
        log.info("DB: Notification saved for user: {}. Message: {}", receiver.getUserId(), message);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sseEmitterService.sendToClient(receiver.getUserId(), "notification", NotificationDto.fromEntity(notification));
                long unreadCount = getUnreadNotificationCount(receiver.getUserId());
                sseEmitterService.sendToClient(receiver.getUserId(), "unreadCount", String.valueOf(unreadCount));
            }
        });
    }

    @Transactional(readOnly = true)
    public long getUnreadNotificationCount(String userId) {
        UserEntity user = userRepository.findByUserId(userId);
        return notificationRepository.countByReceiverAndIsReadFalse(user);
    }
    
    @Scheduled(fixedRate = 15000)
    public void sendHeartbeat() {
        sseEmitterService.getEmitters().forEach((userId, emitter) -> {
            sseEmitterService.sendToClient(userId, "heartbeat", "ping");
        });
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> getNotifications(String userId) {
        UserEntity user = userRepository.findByUserId(userId);
        return notificationRepository.findByReceiverAndIsReadFalseOrderByCreatedAtDesc(user)
                .stream()
                .map(NotificationDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public void readNotification(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 알림입니다."));
        notification.setRead(true);
        
        // [✅ 추가] 단일 읽음 처리 후에도 unreadCount를 다시 보내주면 더 안정적입니다.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                String userId = notification.getReceiver().getUserId();
                long unreadCount = getUnreadNotificationCount(userId);
                sseEmitterService.sendToClient(userId, "unreadCount", String.valueOf(unreadCount));
            }
        });
    }

    @Transactional
    public void readAllNotifications(String userId) {
        UserEntity user = userRepository.findByUserId(userId);
        notificationRepository.markAllAsReadByUser(user);
        
        // [✅ 핵심 수정] '모두 읽음' 처리 후, 변경된 unreadCount(0)를 클라이언트에 전송합니다.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sseEmitterService.sendToClient(userId, "unreadCount", "0");
            }
        });
    }
}