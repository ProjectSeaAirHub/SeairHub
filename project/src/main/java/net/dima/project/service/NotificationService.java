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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j // [✅ @Slf4j 추가 확인]
public class NotificationService {

    private static final Long DEFAULT_TIMEOUT = 60L * 60 * 1000; // 1시간
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String userId) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);
        emitters.put(userId, emitter);
        log.info("SSE: New emitter added for user: {}", userId);

        emitter.onCompletion(() -> {
            emitters.remove(userId);
            log.info("SSE: Emitter completed for user: {}", userId);
        });
        emitter.onTimeout(() -> {
            emitters.remove(userId);
            log.info("SSE: Emitter timed out for user: {}", userId);
        });
        emitter.onError(e -> {
            log.error("SSE: Emitter error for user: {}", userId, e);
            emitters.remove(userId);
        });

        // 연결 확인 및 초기 데이터 전송
        sendToClient(userId, "connected", "SSE connection established.");
        long unreadCount = notificationRepository.countByReceiverAndIsReadFalse(userRepository.findByUserId(userId));
        sendToClient(userId, "unreadCount", unreadCount);

        return emitter;
    }

    @Transactional
    public void sendNotification(UserEntity receiver, String message, String url) {
        Notification notification = Notification.builder()
                .receiver(receiver)
                .message(message)
                .url(url)
                .isRead(false)
                .build();
        
        notificationRepository.save(notification);
        log.info("SSE: Notification saved for user: {}. Message: {}", receiver.getUserId(), message);

        sendToClient(receiver.getUserId(), "notification", NotificationDto.fromEntity(notification));
    }

    private void sendToClient(String userId, String eventName, Object data) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter != null) {
            try {
                log.info("SSE: Sending event '{}' to user: {}. Data: {}", eventName, userId, data);
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException e) {
                log.error("SSE: Failed to send event to user {}. Removing emitter.", userId, e);
                emitters.remove(userId);
            }
        } else {
            log.warn("SSE: No emitter found for user: {}. Notification will be delivered upon next connection.", userId);
        }
    }
    
    // [✅ 아래 Heartbeat 메서드 추가]
    /**
     * 30초마다 모든 클라이언트에게 연결 유지를 위한 더미 이벤트를 보냅니다.
     */
    @Scheduled(fixedRate = 30000)
    public void sendHeartbeat() {
        emitters.forEach((userId, emitter) -> {
            try {
                emitter.send(SseEmitter.event().name("heartbeat").data("ping"));
            } catch (IOException e) {
                // 이 에러는 클라이언트가 브라우저를 닫는 등 정상적으로 연결이 끊어졌을 때 발생하므로, 경고 레벨로 기록합니다.
                log.warn("Heartbeat failed for user {}, removing emitter.", userId);
                emitters.remove(userId);
            }
        });
    }


    @Transactional(readOnly = true)
    public List<NotificationDto> getNotifications(String userId) {
        UserEntity user = userRepository.findByUserId(userId);
        return notificationRepository.findByReceiverOrderByCreatedAtDesc(user)
                .stream()
                .map(NotificationDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public void readNotification(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 알림입니다."));
        notification.setRead(true);
    }

    @Transactional
    public void readAllNotifications(String userId) {
        UserEntity user = userRepository.findByUserId(userId);
        notificationRepository.markAllAsReadByUser(user);
    }
}