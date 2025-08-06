package net.dima.project.repository;

import net.dima.project.entity.ChatMessage;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
	
    // [이 메서드를 추가해주세요]
    List<ChatMessage> findByChatRoom_ChatRoomIdOrderBySentAtAsc(Long chatRoomId);
}