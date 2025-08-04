package net.dima.project.dto;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import lombok.*;
import net.dima.project.entity.UserEntity;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class LoginUserDetails implements UserDetails, OAuth2User {

    private String userId;
    private String userPwd;
    private String userName;
    private String roles;
    private Map<String, Object> attributes;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(roles));
    }

    @Override
    public String getPassword() {
        return userPwd;
    }

    @Override
    public String getUsername() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public String getName() {
        return userName;
    }

    /**
     * [수정] 혼란을 피하기 위해 원래 코드의 메서드 이름인 toResp로 복원합니다.
     * UserEntity(DB 데이터)를 LoginUserDetails(Spring Security용 데이터)로 변환
     */
    public static LoginUserDetails toResp(UserEntity userEntity) {
        return LoginUserDetails.builder()
            .userId(userEntity.getUserId())
            .userPwd(userEntity.getUserPwd())
            .userName(userEntity.getUserName())
            .roles(userEntity.getRoles())
            .build();
    }
}