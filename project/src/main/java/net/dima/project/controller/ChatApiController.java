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
import org.springframework.web.bind.annotation.PostMapping; 
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

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
    
    // [이 메서드를 추가해주세요]
    @PutMapping("/rooms/{roomId}/name")
    public ResponseEntity<Void> updateChatRoomName(
            @PathVariable("roomId") Long roomId,
            @RequestBody Map<String, String> payload,
            @AuthenticationPrincipal LoginUserDetails userDetails) {
        
        String newName = payload.get("name");
        if (newName == null || newName.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        
        chatService.updateChatRoomName(userDetails.getUserSeq(), roomId, newName.trim());
        return ResponseEntity.ok().build();
    }
    
    // [✅ 추가] 메시지 읽음 처리 API
    @PostMapping("/rooms/{roomId}/read")
    public ResponseEntity<Void> markMessagesAsRead(
            @PathVariable("roomId") Long roomId,
            @AuthenticationPrincipal LoginUserDetails userDetails) {
        chatService.markMessagesAsRead(roomId, userDetails.getUserSeq());
        return ResponseEntity.ok().build();
    }
}