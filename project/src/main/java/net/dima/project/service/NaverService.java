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
public class NaverService extends DefaultOAuth2UserService {
 
 private final UserRepository userRepository;
 
 @Override
 public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
     log.info("네이버 OAuth2 로그인 시작");
     
     OAuth2User oauth2User = super.loadUser(userRequest);
     
     // 네이버는 'response' 라는 이름의 Map 안에 실제 사용자 정보가 들어있습니다.
     Map<String, Object> response = oauth2User.getAttribute("response");

     String provider = "naver";
     // 'response' 맵에서 고유 ID를 문자열로 추출합니다.
     String providerId = response.get("id").toString();
     
     UserEntity existingUser = userRepository.findByProviderAndProviderId(provider, providerId);
     
     // 이미 가입된 사용자인 경우
     if (existingUser != null) {
         log.info("기존 네이버 사용자입니다. 로그인 처리를 진행합니다: {}", existingUser.getUserId());
      // [기존 코드]
      // return LoginUserDetails.builder() ...

      // [수정 후 코드]
         return LoginUserDetails.builder()
        		    .userSeq(existingUser.getUserSeq()) // userSeq 추가
        		    .userId(existingUser.getUserId())
        		    .userPwd(existingUser.getUserPwd())
        		    .userName(existingUser.getUserName())
        		    .roles(existingUser.getRoles())
        		    .attributes(oauth2User.getAttributes()) // 또는 response
        		    .build();
     }
     
     // 신규 사용자인 경우
     log.info("신규 네이버 사용자입니다. 추가 정보 입력이 필요합니다.");
     // 'response' 맵에서 이메일을 추출합니다.
     String email = (String) response.get("email");
     
     // 세션에 카카오/네이버 정보를 공통된 이름으로 저장하여 Controller에서 처리하기 쉽게 합니다.
     ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
     HttpSession session = attr.getRequest().getSession(true);
     session.setAttribute("social_provider", provider);
     session.setAttribute("social_provider_id", providerId);
     session.setAttribute("social_email", email);

     // 특정 예외를 발생시켜 FailureHandler가 회원가입 페이지로 유도하도록 합니다.
     throw new OAuth2AuthenticationException(new OAuth2Error("additional_info_required"), "additional_info_required");
 }
}