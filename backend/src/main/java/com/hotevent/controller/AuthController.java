package com.hotevent.controller;

import com.hotevent.common.Result;
import com.hotevent.dto.LoginRequest;
import com.hotevent.dto.LoginResponse;
import com.hotevent.dto.UserVO;
import com.hotevent.entity.User;
import com.hotevent.security.JwtUtil;
import com.hotevent.service.UserService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        User user = (User) authentication.getPrincipal();
        if (!user.isEnabled()) {
            return Result.error("账号已被禁用");
        }
        String token = jwtUtil.generateToken(user);
        UserVO userVO = userService.toVO(user);
        log.info("用户登录成功: {}", user.getUsername());
        return Result.success("登录成功", new LoginResponse(token, userVO));
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
