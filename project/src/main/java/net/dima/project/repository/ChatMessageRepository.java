package net.dima.project.repository;

import net.dima.project.entity.ChatMessage;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying; // [✅ 추가]
import org.springframework.data.jpa.repository.Query; // [✅ 추가]
import org.springframework.data.repository.query.Param; // [✅ 추가]

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
	
    List<ChatMessage> findByChatRoom_ChatRoomIdOrderBySentAtAsc(Long chatRoomId);

    // [✅ 추가] 특정 채팅방에서 특정 사용자가 읽지 않은 메시지 개수 조회
    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.chatRoom.chatRoomId = :roomId AND m.sender.userSeq != :userSeq AND m.isRead = false")
    long countUnreadMessages(@Param("roomId") Long roomId, @Param("userSeq") Integer userSeq);

    // [✅ 추가] 특정 채팅방의 메시지를 모두 읽음 처리
    @Modifying
    @Query("UPDATE ChatMessage m SET m.isRead = true WHERE m.chatRoom.chatRoomId = :roomId AND m.sender.userSeq != :userSeq")
    void markAsReadByRoomIdAndUserSeq(@Param("roomId") Long roomId, @Param("userSeq") Integer userSeq);
}