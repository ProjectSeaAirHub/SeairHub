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
    private final SseEmitterService sseEmitterService; // [✅ 추가]

    public void createChatRoomForOffer(OfferEntity offer) {
        if (chatRoomRepository.findByOffer(offer).isPresent()) {
            return;
        }

        ChatRoom chatRoom = ChatRoom.builder().offer(offer).build();

        UserEntity requester = offer.getRequest().getRequester();
        UserEntity provider = offer.getForwarder();

        ChatParticipant requesterParticipant = ChatParticipant.builder()
                .chatRoom(chatRoom).user(requester).roleInChat("REQUESTER").build();

        ChatParticipant providerParticipant = ChatParticipant.builder()
                .chatRoom(chatRoom).user(provider).roleInChat("PROVIDER").build();

        chatRoom.getParticipants().add(requesterParticipant);
        chatRoom.getParticipants().add(providerParticipant);

        chatRoomRepository.save(chatRoom);
    }

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

    // [✅ 수정] 메시지 저장 시 SSE 이벤트 발생 로직 추가
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

        // 트랜잭션 커밋 후 SSE 이벤트 전송
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

    // [✅ 추가] 메시지 읽음 처리 메서드
    public void markMessagesAsRead(Long roomId, Integer userSeq) {
        chatMessageRepository.markAsReadByRoomIdAndUserSeq(roomId, userSeq);

        // 트랜잭션 커밋 후 SSE 이벤트 전송
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

        // [✅ 추가] 안 읽은 메시지 수 계산
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