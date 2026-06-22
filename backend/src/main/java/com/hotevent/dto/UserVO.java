package com.hotevent.dto;

import com.hotevent.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserVO {
    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String avatar;
    private Role role;
    private Boolean enabled;
    private Integer loginFailCount;
    private LocalDateTime lastLoginFailTime;
    private LocalDateTime lockTime;
    private Boolean locked;
    private LocalDateTime createTime;

    public boolean isLocked() {
        return Boolean.TRUE.equals(locked);
    }
}
