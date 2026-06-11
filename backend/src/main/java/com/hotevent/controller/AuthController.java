package com.hotevent.controller;

import com.hotevent.common.Result;
import com.hotevent.dto.LoginRequest;
import com.hotevent.dto.LoginResponse;
import com.hotevent.dto.UserVO;
import com.hotevent.entity.User;
import com.hotevent.repository.UserRepository;
import com.hotevent.security.JwtUtil;
import com.hotevent.service.SysConfigService;
import com.hotevent.service.UserService;
import com.hotevent.util.RsaUtil;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserService userService;

    @Autowired
    private RsaUtil rsaUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SysConfigService sysConfigService;

    @GetMapping("/public-key")
    public Result<String> getPublicKey() {
        return Result.success(rsaUtil.getPublicKey());
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        String username = request.getUsername();
        Optional<User> userOpt = userRepository.findByUsername(username);

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (!user.isAccountNonLocked()) {
                LocalDateTime lockTime = user.getLockTime();
                String lockTimeStr = lockTime != null ? lockTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "";
                log.warn("用户 {} 尝试登录但账号已被锁定，锁定至: {}", username, lockTimeStr);
                return Result.error("账号已被锁定，请于 " + lockTimeStr + " 后再试");
            }
        }

        try {
            String decryptedPassword = rsaUtil.decryptIfNeeded(request.getPassword());
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, decryptedPassword)
            );
            User user = (User) authentication.getPrincipal();
            if (!user.isEnabled()) {
                return Result.error("账号已被禁用");
            }
            userService.resetLoginFailuresOnSuccess(username);
            String token = jwtUtil.generateToken(user);
            UserVO userVO = userService.toVO(user);
            log.info("用户登录成功: {}", username);
            return Result.success("登录成功", new LoginResponse(token, userVO));
        } catch (BadCredentialsException e) {
            userService.recordLoginFailure(username);
            User user = userRepository.findByUsername(username).orElse(null);
            if (user != null && user.isLocked()) {
                LocalDateTime lockTime = user.getLockTime();
                String lockTimeStr = lockTime != null ? lockTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "";
                return Result.error("密码错误次数过多，账号已被锁定，请于 " + lockTimeStr + " 后再试");
            }
            int remainAttempts = Math.max(0, sysConfigService.getMaxLoginAttempts() -
                    (user != null && user.getLoginFailCount() != null ? user.getLoginFailCount() : 0));
            String msg = "用户名或密码错误";
            if (remainAttempts > 0) {
                msg += "，剩余尝试次数: " + remainAttempts;
            }
            log.warn("用户 {} 登录失败，密码错误", username);
            return Result.error(msg);
        } catch (LockedException e) {
            User user = userRepository.findByUsername(username).orElse(null);
            LocalDateTime lockTime = user != null ? user.getLockTime() : null;
            String lockTimeStr = lockTime != null ? lockTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "";
            log.warn("用户 {} 尝试登录但账号已被锁定", username);
            return Result.error("账号已被锁定，请于 " + lockTimeStr + " 后再试");
        } catch (DisabledException e) {
            log.warn("用户 {} 尝试登录但账号已被禁用", username);
            return Result.error("账号已被禁用");
        } catch (AuthenticationException e) {
            userService.recordLoginFailure(username);
            log.warn("用户 {} 登录失败: {}", username, e.getMessage());
            return Result.error("登录失败: " + e.getMessage());
        }
    }

    @GetMapping("/me")
    public Result<UserVO> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return Result.success(userService.toVO(user));
        }
        return Result.error(401, "未登录");
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        SecurityContextHolder.clearContext();
        return Result.success("登出成功", null);
    }
}
