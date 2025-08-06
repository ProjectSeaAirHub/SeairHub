package net.dima.project.service;

import java.util.Map;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dima.project.dto.LoginUserDetails;
import net.dima.project.entity.UserEntity;
import net.dima.project.repository.UserRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class KakaoService extends DefaultOAuth2UserService {
    
    private final UserRepository userRepository;
    
    //OAuth2UserRequest userRequest: Spring Security가 카카오와 통신한 모든 결과물(엑세스 토큰 등)이 담겨있음. 이 결과물을 super.loadUser()에 전달해서 최종 사용자 정보를 꺼낼 겁니다.

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        log.info("카카오 OAuth2 로그인 시작");
        
        OAuth2User oauth2User = super.loadUser(userRequest);
        
        String provider = "kakao";
        String providerId = oauth2User.getAttribute("id").toString();
        
        UserEntity existingUser = userRepository.findByProviderAndProviderId(provider, providerId);
        
        // 이미 가입된 사용자인 경우
        if (existingUser != null) {
            log.info("기존 카카오 사용자입니다. 로그인 처리를 진행합니다: {}", existingUser.getUserId());
            
            // [오류 수정]
            // 여기서 `attributes`를 포함하여 객체를 생성해야 합니다.
            // 공통 메서드를 사용하는 대신, 원래 코드처럼 builder를 직접 사용합니다.
         // [수정 후 코드]
            return LoginUserDetails.builder()
                .userSeq(existingUser.getUserSeq()) // userSeq 추가
                .userId(existingUser.getUserId())
                .userPwd(existingUser.getUserPwd())
                .userName(existingUser.getUserName())
                .roles(existingUser.getRoles())
                .attributes(oauth2User.getAttributes())
                .build();
        }
        
        // 신규 사용자인 경우
        log.info("신규 카카오 사용자입니다. 추가 정보 입력이 필요합니다.");
        // 5-1. 'kakao_account' 라는 이름의 더 상세한 정보 서랍장을 통째로 꺼냄.
        // "oauth2User"라는 가장 큰 서랍장에서,
        // 이름이 "kakao_account"인 서랍을 열어줘!
        //  Map<String, Object> = string 이름표를 가진 서랍장 타입임을 암시.
        Map<String, Object> kakaoAccount = oauth2User.getAttribute("kakao_account");
        // 5-2. 상세 정보 서랍장에서 'email' 서랍을 열어 이메일 주소를 꺼냄.
        String email = (String) kakaoAccount.get("email");
        
        // 세션에 카카오 정보를 저장
        ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpSession session = attr.getRequest().getSession(true);
        session.setAttribute("kakao_provider", provider);
        session.setAttribute("kakao_provider_id", providerId);
        session.setAttribute("kakao_email", email);

        // 특정 예외를 발생시켜 FailureHandler가 처리하도록 함
        throw new OAuth2AuthenticationException(new OAuth2Error("additional_info_required"), "additional_info_required");
    }
}