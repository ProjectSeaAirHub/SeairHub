package net.dima.project.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import net.dima.project.dto.LoginUserDetails;
import net.dima.project.entity.UserEntity;
import net.dima.project.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class LoginService implements UserDetailsService {
    
    private final UserRepository userRepository;
    
    @Override
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        UserEntity userData = userRepository.findByUserId(userId);
        
        if (userData != null) {
            // [수정] toResp 메서드를 호출하도록 다시 변경합니다.
            return LoginUserDetails.toResp(userData);
        }
        
        throw new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + userId);
    }
}