package net.dima.project.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatRoomDto {
    private Long chatRoomId;
    private String roomName; // 예: "[화주] 쌤송전자 '재판매용 반도체'"
    private String lastMessage;
    private String lastMessageTime;
    private int unreadCount;
}