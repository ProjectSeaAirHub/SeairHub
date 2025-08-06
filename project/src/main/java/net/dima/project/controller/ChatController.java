package net.dima.project.controller;

import lombok.RequiredArgsConstructor;
import net.dima.project.dto.ChatMessageDto;
import net.dima.project.entity.ChatMessage;
import net.dima.project.service.ChatService;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final SimpMessageSendingOperations messagingTemplate;
    private final ChatService chatService; // ChatService 주입

    @MessageMapping("/chat/sendMessage")
    public void sendMessage(ChatMessageDto chatMessageDto) {
        // 1. 받은 메시지를 DB에 저장
        ChatMessage savedMessage = chatService.saveMessage(chatMessageDto);

        // 2. DTO로 변환하여 클라이언트에 전송
        ChatMessageDto messageToSend = ChatMessageDto.fromEntity(savedMessage);
        
        messagingTemplate.convertAndSend("/topic/chatroom/" + messageToSend.getChatRoomId(), messageToSend);
    }
}