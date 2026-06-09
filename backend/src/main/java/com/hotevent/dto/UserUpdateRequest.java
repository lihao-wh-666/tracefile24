package com.hotevent.dto;

import com.hotevent.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserUpdateRequest {

    @Size(min = 6, max = 100, message = "密码长度应在6-100之间")
    private String password;

    private String nickname;

    @Email(message = "邮箱格式不正确")
    private String email;

    private Role role;

    private Boolean enabled;
}
