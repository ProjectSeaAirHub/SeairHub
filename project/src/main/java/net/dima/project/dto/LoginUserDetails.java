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
	private Integer userSeq; // [이 줄을 추가해주세요]
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
    // [아래 getUserSeq 메서드를 추가해주세요]
    public Integer getUserSeq() {
        return userSeq;
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
     * UserEntity(DB 데이터)를 LoginUserDetails(Spring Security용 데이터)로 변환
     * [이 메서드를 아래 코드로 교체해주세요]
     */
    public static LoginUserDetails toResp(UserEntity userEntity) {
        return LoginUserDetails.builder()
            .userSeq(userEntity.getUserSeq()) // userSeq 추가
            .userId(userEntity.getUserId())
            .userPwd(userEntity.getUserPwd())
            .userName(userEntity.getUserName())
            .roles(userEntity.getRoles())
            .build();
    }

}