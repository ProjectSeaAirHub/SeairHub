package net.dima.project.config;

import java.io.IOException;
import java.util.Collection;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * 로그인 성공 후 처리를 담당하는 커스텀 핸들러.
 * 일반 로그인, 소셜 로그인 모두 성공 시 이 핸들러가 동작한다.
 */
@Slf4j
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    
    // Authentication authentication : 사용자의 아이디와 roles가 들어있음 -> dto를 거치지 않기 때문에 씀 그리고 dto는 검증 전의 데이터고 authentication 검증 후의 인증된 정보가 담기는 거임.
    // HttpServletResponse response 로그인 성공 후 어느 링크로 가면 될 지 알려주기 위함. response.sendRedirect(targetUrl);
    // HttpServletRequest request 서버에 요청을 보냄 / 어느 ip에서 왔는지.. 어떤 액션을 통해 get해서 온건지 등의 정보를 담고 있음.
    public void onAuthenticationSuccess(
    		HttpServletRequest request
    		, HttpServletResponse response
    		, Authentication authentication) throws IOException, ServletException {
        
        // 1. 인증된 사용자의 권한(role) 정보를 가져온다.
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        log.info("로그인 성공! 사용자: {}, 권한: {}", authentication.getName(), authorities);
        
        // 2. 기본 리디렉션 URL을 설정한다.
        String targetUrl = "/";
        
        // 3. 권한에 따라 리디렉션할 URL을 결정한다.
        for (GrantedAuthority authority : authorities) {
            String role = authority.getAuthority();
            
         // [✅ 추가] 관리자(ROLE_admin)를 가장 먼저 확인
            if ("ROLE_PENDING".equals(role)) {
                targetUrl = "/pending-approval-page"; // 승인 대기 안내 페이지로
                break;
            } else if ("ROLE_admin".equals(role)) {
                targetUrl = "/adm/dashboard";
                break;
            } else if ("ROLE_fwd".equals(role)) {
                targetUrl = "/fwd/fwdRequest"; // 포워더는 포워더 대시보드로
                break;
            } else if ("ROLE_cus".equals(role)) {
                targetUrl = "/cus/cusRequest"; // 화주는 화주 대시보드로
                break;
            }
        }
        
        // 4. 결정된 URL로 사용자를 리디렉션 시킨다.
        log.info("역할 기반 리디렉션. URL: {}", targetUrl);
        response.sendRedirect(targetUrl);
    }
}