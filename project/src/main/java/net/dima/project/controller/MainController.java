package net.dima.project.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MainController {
    @GetMapping("/")
    public String index() {
        return "index";
    }

    // [추가] 승인 대기 안내 페이지를 보여주는 메서드
    @GetMapping("/pending-approval-page")
    public String approvalPendingPage(Model model) {
        // 현재 로그인한 사용자의 정보를 가져와 모델에 추가
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        model.addAttribute("username", authentication.getName());
        return "user/approval_pending";
    }
    
    
}

