package net.dima.project.service;

import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dima.project.dto.UserDTO;
import net.dima.project.entity.UserEntity;
import net.dima.project.repository.UserRepository;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher; // ✅ import 추가
import net.dima.project.entity.NotificationEvents;

/**
 * 사용자 관련 비즈니스 로직을 처리하는 서비스 클래스
 */
@Service
@RequiredArgsConstructor
@Transactional // 메서드 내 작업이 모두 성공해야 DB에 반영 (하나라도 실패 시 롤백)
@Slf4j
public class UserService {
    private final UserRepository repository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    // [수정] application.properties에 정의된 파일 업로드 경로를 주입받음
    @Value("${file.upload-dir}")
    private String uploadPath;

    /**
     * 일반 회원가입 처리 (파일 업로드 기능 포함)
     * @param userDTO 가입 정보를 담은 DTO
     * @param role 할당할 권한 ("ROLE_fwd" 또는 "ROLE_cus")
     */
    public void joinProc(UserDTO userDTO, String role) throws IOException {
        log.info("회원가입 처리 시작. 역할: {}", role);
        
        // --- 파일 업로드 처리 ---
        MultipartFile file = userDTO.getBusinessLicenseFile();
        
        if (file != null && !file.isEmpty()) {
            String originalFilename = file.getOriginalFilename();
            // 파일명이 겹치지 않도록 UUID를 이용해 고유한 저장용 파일명 생성
            String savedFilename = UUID.randomUUID().toString() + "_" + originalFilename;

            // DTO에 파일 이름 정보 설정
            userDTO.setBusinessLicenseOrigName(originalFilename);
            userDTO.setBusinessLicenseSavedName(savedFilename);
            
            // 지정된 경로에 실제 파일 저장
            File saveDir = new File(uploadPath);
            if (!saveDir.exists()) {
                saveDir.mkdirs(); // 폴더가 없으면 생성
            }
            File saveFile = new File(uploadPath, savedFilename);
            file.transferTo(saveFile);

            log.info("파일 저장 완료: {}", saveFile.getAbsolutePath());
        }

        // 비밀번호를 암호화
        userDTO.setUserPwd(bCryptPasswordEncoder.encode(userDTO.getUserPwd()));
        // 역할 설정
        userDTO.setRoles(role);
        
        // [추가] 포워더(fwd)로 가입 시, 기본 승인 상태를 PENDING으로 설정
        // [수정] 역할을 설정하는 로직을 아래와 같이 변경합니다.
        if ("ROLE_fwd".equals(role)) {
            // 포워더로 가입 신청 시, 역할은 '승인 대기'로, 승인 상태도 'PENDING'으로 설정
            userDTO.setRoles("ROLE_PENDING");
            userDTO.setApprovalStatus("PENDING");
        } else {
            // 화주는 즉시 승인 및 역할 부여
            userDTO.setRoles("ROLE_cus");
            userDTO.setApprovalStatus("APPROVED");
        }

        // DTO를 Entity로 변환하여 DB에 저장
        UserEntity userEntity = UserEntity.toEntity(userDTO);
        repository.save(userEntity);
        log.info("사용자 DB 저장 완료. 아이디: {}", userEntity.getUserId());
        eventPublisher.publishEvent(new NotificationEvents.UserJoinedEvent(this));
    }

    /**
     * 카카오 로그인 사용자의 추가 정보 입력 후 최종 회원가입 처리
     * @param userDTO 추가 정보가 포함된 DTO
     */
    public void completeKakaoRegistration(UserDTO userDTO) {
        log.info("카카오 사용자 최종 등록 처리 시작. 아이디: {}", userDTO.getUserId());
        
        // 카카오 사용자는 provider와 providerId가 이미 DTO에 설정되어 있어야 함
        // 비밀번호 암호화 및 역할 설정
        userDTO.setUserPwd(bCryptPasswordEncoder.encode(userDTO.getUserPwd()));
        // 역할은 가입 페이지에 따라 이미 DTO에 담겨 있어야 함.
        
        UserEntity userEntity = UserEntity.toEntity(userDTO);
        repository.save(userEntity);
        log.info("카카오 사용자 DB 저장 완료");
    }

    // 아이디 중복 확인
    public boolean isUserIdAvailable(String userId) {
        if (userId == null || userId.trim().length() < 5 || userId.trim().length() > 10) {
            return false;
        }
        return repository.findByUserId(userId) == null;
    }
    
    // 이메일 중복 확인
    public boolean isEmailAvailable(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        return repository.findByEmail(email) == null;
    }
}