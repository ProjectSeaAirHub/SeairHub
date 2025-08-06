package net.dima.project.service;

import lombok.RequiredArgsConstructor;
import net.dima.project.dto.MyInfoDto;
import net.dima.project.entity.UserEntity;
import net.dima.project.repository.UserRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyPageService {

    private final UserRepository userRepository;

    public MyInfoDto getUserInfo(String userId) {
        UserEntity userEntity = userRepository.findByUserId(userId);
        if (userEntity == null) {
            throw new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + userId);
        }
        return MyInfoDto.from(userEntity);
    }
}