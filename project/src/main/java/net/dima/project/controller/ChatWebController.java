package net.dima.project.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class ChatWebController {

    // private final ChatService chatService; // 추후 채팅방 목록 조회 시 사용

    @GetMapping("/chat")
    public String chatPage(Authentication authentication, Model model) {
        String userId = authentication.getName();
        // TODO: chatService.getChatRoomsForUser(userId)를 호출하여 채팅방 목록을 모델에 추가하는 로직 필요

        // 사용자의 역할에 따라 올바른 사이드바가 표시되도록 activeMenu 값을 전달합니다.
        model.addAttribute("activeMenu", "chat");
        return "chat"; // templates/chat.html 파일을 렌더링
    }
}