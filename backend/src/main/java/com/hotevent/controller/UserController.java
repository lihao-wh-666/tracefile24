package com.hotevent.controller;

import com.hotevent.common.PageResult;
import com.hotevent.common.Result;
import com.hotevent.dto.UserCreateRequest;
import com.hotevent.dto.UserUpdateRequest;
import com.hotevent.dto.UserVO;
import com.hotevent.service.UserService;
import com.hotevent.util.RsaUtil;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private RsaUtil rsaUtil;

    @GetMapping
    public Result<List<UserVO>> list() {
        return Result.success(userService.listAll());
    }

    @GetMapping("/page")
    public Result<PageResult<UserVO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<UserVO> pageData = userService.page(pageable);
        return Result.success(PageResult.of(
                pageData.getContent(),
                pageData.getTotalElements(),
                pageData.getNumber() + 1,
                pageData.getSize()
        ));
    }

    @GetMapping("/{id}")
    public Result<UserVO> getById(@PathVariable Long id) {
        return Result.success(userService.getById(id));
    }

    @PostMapping
    public Result<UserVO> create(@Valid @RequestBody UserCreateRequest request) {
        request.setPassword(rsaUtil.decryptIfNeeded(request.getPassword()));
        return Result.success("创建成功", userService.create(request));
    }

    @PutMapping("/{id}")
    public Result<UserVO> update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            request.setPassword(rsaUtil.decryptIfNeeded(request.getPassword()));
        }
        return Result.success("更新成功", userService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.success("删除成功", null);
    }

    @PostMapping("/{id}/unlock")
    public Result<UserVO> unlock(@PathVariable Long id) {
        return Result.success("解锁成功", userService.unlockUser(id));
    }
}
