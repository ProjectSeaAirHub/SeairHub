// [✅ /service/ChatService.java 파일 전체를 이 최종 코드로 교체해주세요]
package net.dima.project.service;

import lombok.RequiredArgsConstructor;
import net.dima.project.dto.ChatMessageDto;
import net.dima.project.dto.ChatRoomDto;
import net.dima.project.entity.*;
import net.dima.project.repository.ChatMessageRepository;
import net.dima.project.repository.ChatRoomRepository;
import net.dima.project.repository.ContainerCargoRepository;
import net.dima.project.repository.UserRepository;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Transactional
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ContainerCargoRepository containerCargoRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final SseEmitterService sseEmitterService;

    /**
     * 특정 제안(Offer)에 대한 채팅방을 생성합니다.
     * [✅ 핵심 수정] 재판매 체인을 고려하여 원본 화주와 최종 운송사를 연결합니다.
     */
    public void createChatRoomForOffer(OfferEntity offer) {
        if (chatRoomRepository.findByOffer(offer).isPresent()) {
            return;
        }

        ChatRoom chatRoom = ChatRoom.builder().offer(offer).build();

        // [✅ 수정] 재판매 체인의 원본 화주를 찾습니다.
        UserEntity requester = offer.getRequest().getCargo().getOwner();
        UserEntity provider = offer.getForwarder(); // 최종 운송사

        ChatParticipant requesterParticipant = ChatParticipant.builder()
                .chatRoom(chatRoom).user(requester).roleInChat("REQUESTER").build();

        ChatParticipant providerParticipant = ChatParticipant.builder()
                .chatRoom(chatRoom).user(provider).roleInChat("PROVIDER").build();

        chatRoom.getParticipants().add(requesterParticipant);
        chatRoom.getParticipants().add(providerParticipant);

        chatRoomRepository.save(chatRoom);
    }

    /**
     * 정산 완료된 컨테이너와 관련된 모든 채팅방을 닫습니다. (기존과 동일)
     */
    public void closeChatRoomsForSettledContainer(ContainerEntity container) {
        containerCargoRepository.findAllByContainer(container).stream()
            .filter(cargo -> !cargo.getIsExternal() && cargo.getOffer() != null)
            .forEach(cargo -> closeChatRoomAndUpstream(cargo.getOffer()));
    }
    
    private void closeChatRoomAndUpstream(OfferEntity offer) {
        chatRoomRepository.findByOffer(offer).ifPresent(chatRoom -> {
            chatRoom.setStatus(ChatRoomStatus.CLOSED);
            RequestEntity request = offer.getRequest();
            if (request.getSourceOffer() != null) {
                closeChatRoomAndUpstream(request.getSourceOffer());
            }
        });
    }
    
    @Transactional(readOnly = true)
    public List<ChatRoomDto> getChatRoomsForUser(Integer userSeq) {
        return chatRoomRepository.findAllByUserSeq(userSeq).stream()
                .filter(chatRoom -> chatRoom.getStatus() == ChatRoomStatus.ACTIVE)
                .map(chatRoom -> toChatRoomDto(chatRoom, userSeq))
                .sorted(Comparator.comparing(ChatRoomDto::getLastMessageTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> getMessagesForChatRoom(Long chatRoomId) {
        return chatMessageRepository.findByChatRoom_ChatRoomIdOrderBySentAtAsc(chatRoomId).stream()
                .map(ChatMessageDto::fromEntity)
                .collect(Collectors.toList());
    }

    public ChatMessage saveMessage(ChatMessageDto dto) {
        UserEntity sender = userRepository.findById(dto.getSenderSeq())
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));
        ChatRoom chatRoom = chatRoomRepository.findById(dto.getChatRoomId())
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));

        ChatMessage chatMessage = ChatMessage.builder()
                .chatRoom(chatRoom)
                .sender(sender)
                .messageContent(dto.getMessageContent())
                .build();
        
        ChatMessage savedMessage = chatMessageRepository.save(chatMessage);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                chatRoom.getParticipants().stream()
                    .filter(p -> !p.getUser().getUserSeq().equals(sender.getUserSeq()))
                    .findFirst()
                    .ifPresent(receiverParticipant -> {
                        String receiverUserId = receiverParticipant.getUser().getUserId();
                        sseEmitterService.sendToClient(receiverUserId, "unreadChat", "new message");
                    });
            }
        });
        
        return savedMessage;
    }

    public void markMessagesAsRead(Long roomId, Integer userSeq) {
        chatMessageRepository.markAsReadByRoomIdAndUserSeq(roomId, userSeq);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                UserEntity user = userRepository.findById(userSeq).orElse(null);
                if (user != null) {
                    sseEmitterService.sendToClient(user.getUserId(), "unreadChat", "marked as read");
                }
            }
        });
    }

    private ChatRoomDto toChatRoomDto(ChatRoom chatRoom, Integer currentUserSeq) {
        UserEntity otherUser = chatRoom.getParticipants().stream()
                .map(ChatParticipant::getUser)
                .filter(user -> !user.getUserSeq().equals(currentUserSeq))
                .findFirst()
                .orElse(null);

        ChatParticipant myParticipantInfo = chatRoom.getParticipants().stream()
                .filter(p -> p.getUser().getUserSeq().equals(currentUserSeq))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("채팅방 참여 정보를 찾을 수 없습니다."));

        String myRole = myParticipantInfo.getRoleInChat();
        String customName = myParticipantInfo.getCustomRoomName();

        String roomName;
        if (customName != null && !customName.isBlank()) {
            roomName = customName;
        } else {
            String rolePrefix = "REQUESTER".equals(myRole) ? "[운송사]" : "[화주]";
            roomName = String.format("%s %s '%s'", rolePrefix,
                    otherUser != null ? otherUser.getCompanyName() : "알 수 없음",
                    chatRoom.getOffer().getRequest().getCargo().getItemName());
        }

        long unreadCount = chatMessageRepository.countUnreadMessages(chatRoom.getChatRoomId(), currentUserSeq);

        return ChatRoomDto.builder()
                .chatRoomId(chatRoom.getChatRoomId())
                .roomName(roomName)
                .unreadCount((int) unreadCount)
                .build();
    }
    
    public void updateChatRoomName(Integer userSeq, Long chatRoomId, String newName) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));

        ChatParticipant participant = chatRoom.getParticipants().stream()
                .filter(p -> p.getUser().getUserSeq().equals(userSeq))
                .findFirst()
                .orElseThrow(() -> new SecurityException("해당 채팅방에 참여하고 있지 않습니다."));
        
        participant.setCustomRoomName(newName);
    }
}