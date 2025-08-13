package net.dima.project.config;
import net.dima.project.service.NaverService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import lombok.RequiredArgsConstructor;
import net.dima.project.service.KakaoService;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final NaverService naverService;
	private final KakaoService kakaoService;
    private final CustomOAuth2FailureHandler customOAuth2FailureHandler;



    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // [✅ 수정] CORS 설정을 명시적으로 추가
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()));
    	
        http.authorizeHttpRequests(auth -> auth
            .requestMatchers(
                "/", "/login", "/join", 
                "/fwd/join", "/fwd/joinProc",
                "/cus/join", "/cus/joinProc",
                "/kakao/additional-info", "/kakao/complete-registration",
                "/user/confirmId", "/user/confirmEmail",
                "/images/**", "/js/**", "/css/**",
                "/approval-pending" // [추가] 승인 대기 페이지는 모두 접근 가능하도록
                ,"/api/scfi-data" 
                , "/ws-chat/**" 
            ).permitAll()
            .requestMatchers("/api/notifications/**").hasAnyRole("fwd", "cus", "admin")
            .requestMatchers("/download/**").authenticated()
            .requestMatchers("/adm/**").hasRole("admin")
            .requestMatchers("/fwd/**", "/api/fwd/**").hasAnyRole("fwd", "admin") // [수정]
            .requestMatchers("/cus/**", "/api/cus/**").hasAnyRole("cus", "admin") // [수정]
            .requestMatchers("/my/**").hasAnyRole("ADMIN","fwd","cus")
            .requestMatchers("/pending-approval-page").hasRole("PENDING")
            .anyRequest().authenticated()
        );

        // 기본 폼 로그인 설정
        http.formLogin(login -> login
            .loginPage("/login")
            .loginProcessingUrl("/loginProc")
            .usernameParameter("userId")
            .passwordParameter("userPwd")
            .successHandler(customAuthenticationSuccessHandler()) // 커스텀 핸들러 추가
            .failureUrl("/login?error=true")
            .permitAll()
        );

        // OAuth2 로그인 설정 (기존 카카오만)
//        http.oauth2Login(oauth2 -> oauth2
//                .loginPage("/login")
//                .successHandler(customAuthenticationSuccessHandler())
//                .failureHandler(customOAuth2FailureHandler) // [수정] 커스텀 실패 핸들러 추가
//                .userInfoEndpoint(userInfo -> userInfo
//                    .userService(kakaoService))
//            );
        
        // OAuth2 로그인 설정 (카카오 + 네이버)
        http.oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                .successHandler(customAuthenticationSuccessHandler())
                .failureHandler(customOAuth2FailureHandler)
                .userInfoEndpoint(userInfo -> userInfo
                    // 로그인 제공자(카카오/네이버)에 따라 적절한 서비스를 선택하도록 람다식으로 변경
                    .userService((userRequest) -> {
                        // 현재 로그인 진행 중인 서비스가 무엇인지 확인 (e.g., "kakao", "naver")
                        String registrationId = userRequest.getClientRegistration().getRegistrationId();
                        
                        if ("naver".equals(registrationId)) {
                            return naverService.loadUser(userRequest); // 네이버 로그인이면 NaverService 호출
                        } 
                        // 기본값 또는 "kakao"일 경우 KakaoService 호출
                        return kakaoService.loadUser(userRequest);
                    })
                )
            );
        
        // 로그아웃 설정
        http.logout((auth) -> auth
            .logoutUrl("/logout")
            .logoutSuccessUrl("/")
            .invalidateHttpSession(true)
            .permitAll() // 로그아웃 URL은 모두에게 허용
        );
        
        // ⭐ [핵심] 이 부분을 http.csrf(...) 설정 위에 추가해주세요.
        // Vesselfinder의 지도가 iframe으로 삽입될 수 있도록 Content-Security-Policy 헤더를 설정합니다.
        http.headers(headers -> headers
        	.frameOptions(frameOptions -> frameOptions.sameOrigin())
            .contentSecurityPolicy(csp -> csp
                .policyDirectives("frame-src 'self' https://www.vesselfinder.com")
            )
        );

        http.csrf((auth) -> auth.disable());

        return http.build();
    }
    
    // [✅ 추가] CORS 설정을 위한 Bean
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:8080", "https://*.ngrok-free.app"));
        configuration.setAllowedMethods(Arrays.asList("GET","POST", "OPTIONS", "PUT", "DELETE"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationSuccessHandler customAuthenticationSuccessHandler() {
        return new CustomAuthenticationSuccessHandler();
    }

    @Bean
    BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
