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

@Service
@RequiredArgsConstructor
@Transactional
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ContainerCargoRepository containerCargoRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;

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

    /**
     * 컨테이너가 정산완료(SETTLED)될 때, 관련된 모든 채팅방을 종료(CLOSED)시키는 메서드
     * [오류 수정 완료] ContainerCargoRepository를 주입받아 올바르게 조회하도록 수정
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
    
    /**
     * 현재 로그인한 사용자가 참여하고 있는 모든 채팅방 목록을 조회합니다.
     */
    /**
     * [이 메서드를 아래 코드로 교체해주세요]
     * 현재 로그인한 사용자가 참여하고 있는 '활성(ACTIVE)' 상태의 모든 채팅방 목록을 조회합니다.
     */
    @Transactional(readOnly = true)
    public List<ChatRoomDto> getChatRoomsForUser(Integer userSeq) {
        return chatRoomRepository.findAllByUserSeq(userSeq).stream()
                // ⭐ 핵심 수정: 채팅방의 상태가 ACTIVE인 것만 필터링하는 로직 추가
                .filter(chatRoom -> chatRoom.getStatus() == ChatRoomStatus.ACTIVE)
                .map(chatRoom -> toChatRoomDto(chatRoom, userSeq))
                .sorted(Comparator.comparing(ChatRoomDto::getLastMessageTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    /**
     * 특정 채팅방의 모든 메시지 기록을 조회합니다.
     */
    @Transactional(readOnly = true)
    public List<ChatMessageDto> getMessagesForChatRoom(Long chatRoomId) {
        return chatMessageRepository.findByChatRoom_ChatRoomIdOrderBySentAtAsc(chatRoomId).stream()
                .map(ChatMessageDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 클라이언트로부터 받은 새 메시지를 DB에 저장합니다.
     */
    /**
     * [이 메서드를 아래 코드로 교체해주세요]
     */
    public ChatMessage saveMessage(ChatMessageDto dto) {
        // [수정] UserRepository.findById() -> userRepository.findById()
        UserEntity sender = userRepository.findById(dto.getSenderSeq())
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));
        ChatRoom chatRoom = chatRoomRepository.findById(dto.getChatRoomId())
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));

        ChatMessage chatMessage = ChatMessage.builder()
                .chatRoom(chatRoom)
                .sender(sender)
                .messageContent(dto.getMessageContent())
                .build();
        return chatMessageRepository.save(chatMessage);
    }
    /**
     * ChatRoom Entity를 ChatRoomDto로 변환하는 헬퍼 메서드
     */
    private ChatRoomDto toChatRoomDto(ChatRoom chatRoom, Integer currentUserSeq) {
        // 상대방 찾기
        UserEntity otherUser = chatRoom.getParticipants().stream()
                .map(ChatParticipant::getUser)
                .filter(user -> !user.getUserSeq().equals(currentUserSeq))
                .findFirst()
                .orElse(null);

        // 채팅방 참여 정보에서 '나'의 정보 찾기
        ChatParticipant myParticipantInfo = chatRoom.getParticipants().stream()
                .filter(p -> p.getUser().getUserSeq().equals(currentUserSeq))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("채팅방 참여 정보를 찾을 수 없습니다."));

        String myRole = myParticipantInfo.getRoleInChat();
        String customName = myParticipantInfo.getCustomRoomName();

        String roomName;
        // ⭐ 핵심: 커스텀 이름이 있으면 그것을 사용하고, 없으면 자동으로 생성
        if (customName != null && !customName.isBlank()) {
            roomName = customName;
        } else {
            String rolePrefix = "REQUESTER".equals(myRole) ? "[운송사]" : "[화주]";
            roomName = String.format("%s %s '%s'", rolePrefix,
                    otherUser != null ? otherUser.getCompanyName() : "알 수 없음",
                    chatRoom.getOffer().getRequest().getCargo().getItemName());
        }

//        // 채팅방 내 나의 역할 찾기
//        String myRole = chatRoom.getParticipants().stream()
//                .filter(p -> p.getUser().getUserSeq().equals(currentUserSeq))
//                .findFirst()
//                .map(ChatParticipant::getRoleInChat)
//                .orElse("");
//
//        // UX 시나리오에 맞는 채팅방 이름 생성
//        String rolePrefix = "REQUESTER".equals(myRole) ? "[운송사]" : "[화주]";
//        String roomName = String.format("%s %s '%s'", rolePrefix,
//                otherUser != null ? otherUser.getCompanyName() : "알 수 없음",
//                chatRoom.getOffer().getRequest().getCargo().getItemName());

        // 마지막 메시지 정보 (추후 구현)
        // List<ChatMessage> messages = chatRoom.getMessages();
        // ChatMessage lastMessage = messages.isEmpty() ? null : messages.get(messages.size() - 1);

        return ChatRoomDto.builder()
                .chatRoomId(chatRoom.getChatRoomId())
                .roomName(roomName)
                // .lastMessage(lastMessage != null ? lastMessage.getMessageContent() : "대화를 시작해보세요.")
                // .lastMessageTime(lastMessage != null ? lastMessage.getSentAt().format(DateTimeFormatter.ofPattern("MM/dd HH:mm")) : null)
                // .unreadCount(0) // TODO: 안 읽은 메시지 수 계산 로직
                .build();
    }
    
 // ChatService.java
    public void updateChatRoomName(Integer userSeq, Long chatRoomId, String newName) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));

        ChatParticipant participant = chatRoom.getParticipants().stream()
                .filter(p -> p.getUser().getUserSeq().equals(userSeq))
                .findFirst()
                .orElseThrow(() -> new SecurityException("해당 채팅방에 참여하고 있지 않습니다."));
        
        participant.setCustomRoomName(newName);
        // @Transactional 어노테이션 덕분에 메서드가 끝나면 변경사항이 자동으로 DB에 저장됩니다.
    }
}