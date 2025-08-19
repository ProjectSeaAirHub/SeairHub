package net.dima.project.service;

import lombok.extern.slf4j.Slf4j;
import net.dima.project.dto.NotificationDto;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class SseEmitterService {

    private static final Long DEFAULT_TIMEOUT = 60L * 60 * 1000; // 1시간
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * 사용자의 SSE 연결을 생성하고 관리 목록에 추가합니다.
     */
    public SseEmitter createEmitter(String userId) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);
        emitters.put(userId, emitter);
        log.info("SSE: New emitter created for user: {}", userId);

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

        // 연결 확인용 초기 이벤트 전송
        sendToClient(userId, "connected", "SSE connection established.");
        
        return emitter;
    }

    /**
     * 특정 사용자에게 이벤트를 전송합니다.
     */
    public void sendToClient(String userId, String eventName, Object data) {
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
            log.warn("SSE: No emitter found for user: {}", userId);
        }
    }
    
    public Map<String, SseEmitter> getEmitters() {
        return emitters;
    }
}