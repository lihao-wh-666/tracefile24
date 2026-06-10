package com.hotevent.controller;

import com.hotevent.common.Result;
import com.hotevent.dto.PasswordUpdateRequest;
import com.hotevent.dto.ProfileUpdateRequest;
import com.hotevent.dto.UserVO;
import com.hotevent.entity.User;
import com.hotevent.service.UserService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/profile")
public class ProfileController {

    @Autowired
    private UserService userService;

    @Value("${file.upload.path:./uploads}")
    private String uploadPath;

    @Value("${file.upload.url-prefix:/uploads}")
    private String urlPrefix;

    @GetMapping
    public Result<UserVO> getProfile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return Result.success(userService.toVO(user));
        }
        return Result.error(401, "未登录");
    }

    @PutMapping
    public Result<UserVO> updateProfile(@Valid @RequestBody ProfileUpdateRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            UserVO updated = userService.updateProfile(user.getId(), request);
            return Result.success("更新成功", updated);
        }
        return Result.error(401, "未登录");
    }

    @PutMapping("/password")
    public Result<Void> updatePassword(@Valid @RequestBody PasswordUpdateRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            userService.updatePassword(user.getId(), request);
            return Result.success("密码修改成功", null);
        }
        return Result.error(401, "未登录");
    }

    @PostMapping("/avatar")
    public Result<UserVO> uploadAvatar(@RequestParam("file") MultipartFile file) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            if (file.isEmpty()) {
                return Result.error("请选择要上传的文件");
            }

            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return Result.error("只支持上传图片文件");
            }

            long maxSize = 5 * 1024 * 1024;
            if (file.getSize() > maxSize) {
                return Result.error("图片大小不能超过5MB");
            }

            try {
                File uploadDir = new File(uploadPath + "/avatars");
                if (!uploadDir.exists()) {
                    uploadDir.mkdirs();
                }

                String originalFilename = file.getOriginalFilename();
                String extension = originalFilename != null ?
                        originalFilename.substring(originalFilename.lastIndexOf(".")) : ".jpg";
                String filename = UUID.randomUUID().toString().replace("-", "") + extension;

                Path filePath = Paths.get(uploadDir.getAbsolutePath(), filename);
                Files.write(filePath, file.getBytes());

                String avatarUrl = urlPrefix + "/avatars/" + filename;
                UserVO updated = userService.updateAvatar(user.getId(), avatarUrl);

                log.info("用户 {} 上传头像成功: {}", user.getUsername(), avatarUrl);
                return Result.success("上传成功", updated);
            } catch (IOException e) {
                log.error("上传头像失败", e);
                return Result.error("上传失败: " + e.getMessage());
            }
        }
        return Result.error(401, "未登录");
    }
}
