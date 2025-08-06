package net.dima.project.controller;

import lombok.RequiredArgsConstructor;
import net.dima.project.dto.MyInfoDto;
import net.dima.project.service.MyPageService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/my")
@RequiredArgsConstructor
public class MyPageController {

    private final MyPageService myPageService;

    @GetMapping("/info")
    public String myInfoPage(Authentication authentication, Model model) {
        // 현재 로그인한 사용자의 ID를 가져옵니다.
        String userId = authentication.getName();
        
        // 서비스 레이어를 통해 사용자 정보를 조회합니다.
        MyInfoDto userInfo = myPageService.getUserInfo(userId);
        
        // 모델에 사용자 정보를 담아 View로 전달합니다.
        model.addAttribute("userInfo", userInfo);
        
        // 사용자의 역할에 따라 적절한 사이드바가 표시되도록 activeMenu 값을 설정합니다.
        // (이 값은 fragments/sidebar.html 또는 CUS_sidebar.html 에서 사용됩니다)
        model.addAttribute("activeMenu", "myInfo"); 
        
        return "user/my_info";
    }
}