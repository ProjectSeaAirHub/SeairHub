package net.dima.project.config;

import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * OAuth2 로그인 실패 시 처리를 담당하는 커스텀 핸들러
 * 특히, 신규 카카오 사용자를 회원가입 페이지로 유도하는 핵심 역할을 한다.
 */
@Slf4j
@Component // Spring이 이 클래스를 Bean으로 관리하도록 설정
public class CustomOAuth2FailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        
        // 1. KakaoService에서 의도적으로 발생시킨 예외인지 확인
        // 예외 메시지에 "additional_info_required"가 포함되어 있다면, 이는 신규 사용자임을 의미.
        if (exception.getMessage().contains("additional_info_required")) {
            log.info("신규 OAuth2 사용자 확인. 회원 유형 선택 페이지로 리디렉션합니다.");
            // 2. 신규 사용자를 회원 유형 선택 페이지로 보낸다.
            // 이 때, 세션에는 KakaoService가 저장한 카카오 정보가 남아있다.
            getRedirectStrategy().sendRedirect(request, response, "/join");
        } else {
            // 3. 그 외의 모든 로그인 실패(예: 서버 오류, 권한 없음 등)의 경우
            // 기본 실패 URL(SecurityConfig에 설정된)로 보낸다.
            log.error("OAuth2 인증 실패: {}", exception.getMessage());
            super.onAuthenticationFailure(request, response, exception);
        }
    }
}