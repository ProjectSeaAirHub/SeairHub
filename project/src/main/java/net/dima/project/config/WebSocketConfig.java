package net.dima.project.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 클라이언트가 WebSocket 연결을 시작할 엔드포인트입니다.
        // SockJS는 WebSocket을 지원하지 않는 브라우저를 위한 대체 옵션을 제공합니다.
        registry.addEndpoint("/ws-chat").withSockJS();

    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 메시지 브로커가 /topic으로 시작하는 주소를 구독하는 클라이언트에게 메시지를 전달하도록 설정합니다.
        registry.enableSimpleBroker("/topic");
        // 클라이언트가 서버로 메시지를 보낼 때 사용할 주소의 접두사입니다.
        registry.setApplicationDestinationPrefixes("/app");
    }
    
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setSendTimeLimit(15 * 1000) // 메시지 전송 시간 제한 (15초)
                    .setSendBufferSizeLimit(512 * 1024) // 전송 버퍼 크기 제한 (512KB)
                    .setMessageSizeLimit(128 * 1024); // 메시지 크기 제한 (128KB)
    }
}