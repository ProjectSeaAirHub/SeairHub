package net.dima.project.dto;

import lombok.Data;
import net.dima.project.entity.ChatMessage;

@Data
public class ChatMessageDto {
    private Long chatRoomId;
    private Integer senderSeq;
    private String senderName;
    private String messageContent;
    
 // 파일 하단에 fromEntity 메서드를 추가해주세요.
    public static ChatMessageDto fromEntity(ChatMessage entity) {
        ChatMessageDto dto = new ChatMessageDto();
        dto.setChatRoomId(entity.getChatRoom().getChatRoomId());
        dto.setSenderSeq(entity.getSender().getUserSeq());
        dto.setSenderName(entity.getSender().getUserName());
        dto.setMessageContent(entity.getMessageContent());
        return dto;
    }
}

