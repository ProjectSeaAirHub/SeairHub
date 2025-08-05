package net.dima.project.controller;

import lombok.RequiredArgsConstructor;
import net.dima.project.service.NotificationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;


import net.dima.project.dto.NotificationDto;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 현재 로그인한 사용자가 SSE를 구독하는 엔드포인트입니다.
     * produces = MediaType.TEXT_EVENT_STREAM_VALUE는 이 엔드포인트가 SSE 연결을 위한 것임을 명시합니다.
     */
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(Authentication authentication) {
        String userId = authentication.getName();
        return notificationService.subscribe(userId);
    }
    
    
    // [✅ 아래 3개 메서드를 통째로 추가해주세요]

    /**
     * 현재 로그인한 사용자의 모든 알림을 조회합니다.
     */
    @GetMapping
    public ResponseEntity<List<NotificationDto>> getNotifications(Authentication authentication) {
        return ResponseEntity.ok(notificationService.getNotifications(authentication.getName()));
    }

    /**
     * 특정 알림을 읽음 상태로 변경합니다.
     */
    @PostMapping("/{id}/read")
    public ResponseEntity<Void> readNotification(@PathVariable("id") Long id) {
        notificationService.readNotification(id);
        return ResponseEntity.ok().build();
    }

    /**
     * 현재 로그인한 사용자의 모든 알림을 읽음 상태로 변경합니다.
     */
    @PostMapping("/read/all")
    public ResponseEntity<Void> readAllNotifications(Authentication authentication) {
        notificationService.readAllNotifications(authentication.getName());
        return ResponseEntity.ok().build();
    }
}