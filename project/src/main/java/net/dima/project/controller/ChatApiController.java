package net.dima.project.controller;

import lombok.RequiredArgsConstructor;
import net.dima.project.dto.ChatMessageDto;
import net.dima.project.dto.ChatRoomDto;
import net.dima.project.dto.LoginUserDetails;
import net.dima.project.service.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatApiController {

    private final ChatService chatService;

    @GetMapping("/rooms")
    public ResponseEntity<List<ChatRoomDto>> getMyChatRooms(@AuthenticationPrincipal LoginUserDetails userDetails) {
        // [수정] 이제 userDetails.getUserSeq()가 정상적으로 동작합니다.
        List<ChatRoomDto> chatRooms = chatService.getChatRoomsForUser(userDetails.getUserSeq());
        return ResponseEntity.ok(chatRooms);
    }


    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<List<ChatMessageDto>> getChatMessages(@PathVariable("roomId") Long roomId) {
        List<ChatMessageDto> messages = chatService.getMessagesForChatRoom(roomId);
        return ResponseEntity.ok(messages);
    }
}