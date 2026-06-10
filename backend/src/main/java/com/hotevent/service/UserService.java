package com.hotevent.service;

import com.hotevent.dto.UserCreateRequest;
import com.hotevent.dto.UserUpdateRequest;
import com.hotevent.dto.UserVO;
import com.hotevent.entity.Role;
import com.hotevent.entity.User;
import com.hotevent.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public UserVO toVO(User user) {
        return new UserVO(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getEmail(),
                user.getAvatar(),
                user.getRole(),
                user.getEnabled(),
                user.getCreateTime()
        );
    }

    public List<UserVO> listAll() {
        return userRepository.findByDeletedFalse().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    public Page<UserVO> page(Pageable pageable) {
        return userRepository.findByDeletedFalse(pageable)
                .map(this::toVO);
    }

    public UserVO getById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        if (user.getDeleted()) {
            throw new RuntimeException("用户不存在");
        }
        return toVO(user);
    }

    public UserVO getByUsername(String username) {
        User user = userRepository.findByUsernameAndDeletedFalse(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        return toVO(user);
    }

    @Transactional
    public UserVO create(UserCreateRequest request) {
        if (userRepository.existsByUsernameAndDeletedFalse(request.getUsername())) {
            throw new RuntimeException("用户名已存在");
        }
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setRole(request.getRole() != null ? request.getRole() : Role.USER);
        user.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);
        user = userRepository.save(user);
        log.info("创建用户成功: {}", user.getUsername());
        return toVO(user);
    }

    @Transactional
    public UserVO update(Long id, UserUpdateRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        if (user.getDeleted()) {
            throw new RuntimeException("用户不存在");
        }
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getAvatar() != null) {
            user.setAvatar(request.getAvatar());
        }
        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }
        if (request.getEnabled() != null) {
            user.setEnabled(request.getEnabled());
        }
        user = userRepository.save(user);
        log.info("更新用户成功: {}", user.getUsername());
        return toVO(user);
    }

    @Transactional
    public void delete(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        if ("admin".equals(user.getUsername())) {
            throw new RuntimeException("不能删除超级管理员账号");
        }
        user.setDeleted(true);
        userRepository.save(user);
        log.info("删除用户成功: {}", user.getUsername());
    }

    @Transactional
    public UserVO updateProfile(Long userId, com.hotevent.dto.ProfileUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        if (user.getDeleted()) {
            throw new RuntimeException("用户不存在");
        }
        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getAvatar() != null) {
            user.setAvatar(request.getAvatar());
        }
        user = userRepository.save(user);
        log.info("更新个人资料成功: {}", user.getUsername());
        return toVO(user);
    }

    @Transactional
    public void updatePassword(Long userId, com.hotevent.dto.PasswordUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        if (user.getDeleted()) {
            throw new RuntimeException("用户不存在");
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new RuntimeException("原密码不正确");
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("两次输入的新密码不一致");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordVersion(user.getPasswordVersion() != null ? user.getPasswordVersion() + 1 : 1);
        userRepository.save(user);
        log.info("更新密码成功: {}", user.getUsername());
    }

    @Transactional
    public UserVO updateAvatar(Long userId, String avatarUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        if (user.getDeleted()) {
            throw new RuntimeException("用户不存在");
        }
        user.setAvatar(avatarUrl);
        user = userRepository.save(user);
        log.info("更新头像成功: {}", user.getUsername());
        return toVO(user);
    }
}
