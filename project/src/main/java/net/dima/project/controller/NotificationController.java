// [✅ NotificationController.java 파일 전체를 이 최종 코드로 교체해주세요]
package net.dima.project.controller;

import lombok.RequiredArgsConstructor;
import net.dima.project.dto.NotificationDto;
import net.dima.project.service.NotificationService;
import net.dima.project.service.SseEmitterService; // SseEmitterService import
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final SseEmitterService sseEmitterService; // SseEmitterService 주입

    /**
     * SSE 구독 요청을 SseEmitterService에 위임합니다.
     * 이 메소드는 더 이상 @Transactional과 관련이 없습니다.
     */
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(Authentication authentication) {
        String userId = authentication.getName();
        SseEmitter emitter = sseEmitterService.createEmitter(userId);
        
        // 초기 안읽은 알림 개수를 조회하여 클라이언트에 전송합니다.
        // 이 DB 조회는 매우 짧은 트랜잭션으로 처리됩니다.
        long unreadCount = notificationService.getUnreadNotificationCount(userId);
        sseEmitterService.sendToClient(userId, "unreadCount", String.valueOf(unreadCount));
        
        return emitter;
    }

    @GetMapping
    public ResponseEntity<List<NotificationDto>> getNotifications(Authentication authentication) {
        return ResponseEntity.ok(notificationService.getNotifications(authentication.getName()));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> readNotification(@PathVariable("id") Long id) {
        notificationService.readNotification(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/read/all")
    public ResponseEntity<Void> readAllNotifications(Authentication authentication) {
        notificationService.readAllNotifications(authentication.getName());
        return ResponseEntity.ok().build();
    }
}