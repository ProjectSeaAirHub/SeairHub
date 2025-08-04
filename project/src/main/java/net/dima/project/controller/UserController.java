package net.dima.project.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dima.project.dto.UserDTO;
import net.dima.project.service.UserService;
import java.io.IOException;

@Controller
@Slf4j
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    /** 
     * 회원가입 유형 선택 페이지
     */
    @GetMapping("/join")
    public String roleSelect(HttpSession session, Model model) {
        // 카카오 로그인으로 유입된 경우, 세션에 이메일 정보가 있는지 확인
    	if (session.getAttribute("social_email") != null) {
            model.addAttribute("isSocial", true); // 어떤 소셜이든 공통으로 사용
        }
        return "user/roleselect";
    }

    /** 
     * 포워더(fwd) 회원가입 페이지
     */
    @GetMapping("/fwd/join")
    public String fwdJoin(Model model, HttpSession session) {
        // 카카오 로그인 후 이 페이지로 왔다면, 세션에 저장된 이메일을 모델에 담아 전달
        // 이 이메일은 회원가입 폼에 자동으로 채워짐
        if (session.getAttribute("social_email") != null) {
            model.addAttribute("social_email", session.getAttribute("social_email"));
        }
        return "user/fwd_join";
    }

    /** 
     * 화주(cus) 회원가입 페이지
     */
    @GetMapping("/cus/join")
    public String cusJoin(Model model, HttpSession session) {
        // 위와 동일하게 카카오 이메일 정보를 모델에 추가
        if (session.getAttribute("social_email") != null) {
            model.addAttribute("social_email", session.getAttribute("social_email"));
        }
        return "user/cus_join";
    }

    
    
    /** 
     * 포워더 회원가입 처리
     */
    @PostMapping("/fwd/joinProc")
    public String fwdJoinProc(@ModelAttribute UserDTO userDTO, HttpSession session) {
        try {
            // [보완] 카카오 연동 회원가입의 경우, 세션에 저장된 provider 정보를 DTO에 추가
            if (session.getAttribute("social_provider") != null) {
                userDTO.setProvider((String) session.getAttribute("social_provider"));
                userDTO.setProviderId((String) session.getAttribute("social_provider_id"));
                // 처리가 끝난 세션 정보는 제거
                session.removeAttribute("social_provider");
                session.removeAttribute("social_provider_id");
                session.removeAttribute("social_email");
            }
            
            userService.joinProc(userDTO, "ROLE_fwd");
            return "redirect:/login?success=true";
        } catch (IOException e) { // 파일 처리 중 예외 발생 가능
            log.error("포워더 회원가입 실패(파일 저장 오류): ", e);
            return "redirect:/fwd/join?error=true";
        } catch (Exception e) {
            log.error("포워더 회원가입 실패: ", e);
            return "redirect:/fwd/join?error=true";
        }
    }

    /** 
     * 화주 회원가입 처리
     */
    @PostMapping("/cus/joinProc")
    public String cusJoinProc(@ModelAttribute UserDTO userDTO, HttpSession session) {
        try {
            // [보완] 카카오 연동 회원가입의 경우, 위와 동일하게 처리
            if (session.getAttribute("social_provider") != null) {
                userDTO.setProvider((String) session.getAttribute("social_provider"));
                userDTO.setProviderId((String) session.getAttribute("social_provider_id"));
                session.removeAttribute("social_provider");
                session.removeAttribute("social_provider_id");
                session.removeAttribute("social_email");
            }

            userService.joinProc(userDTO, "ROLE_cus");
            return "redirect:/login?success=true";
        } catch (IOException e) {
            log.error("화주 회원가입 실패(파일 저장 오류): ", e);
            return "redirect:/cus/join?error=true";
        } catch (Exception e) {
            log.error("화주 회원가입 실패: ", e);
            return "redirect:/cus/join?error=true";
        }
    }

    /**
     * 로그인 화면
     */
    @GetMapping("/login")
    public String login() {
        return "user/login";
    }
    
    
    /**
     * 아이디 중복 확인 (AJAX 요청 처리)
     */
    @ResponseBody // View를 거치지 않고 데이터 자체를 반환
    @PostMapping("/user/confirmId")
    public boolean confirmId(@RequestParam("userId") String userId) {
        return userService.isUserIdAvailable(userId);
    }
    
    /**
     * 이메일 중복 확인 (AJAX 요청 처리)
     */
    @ResponseBody
    @PostMapping("/user/confirmEmail")
    public boolean confirmEmail(@RequestParam("email") String email) {
        return userService.isEmailAvailable(email);
    }
}