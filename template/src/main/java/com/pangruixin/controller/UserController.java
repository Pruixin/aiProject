package com.pangruixin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.secure.SaSecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pangruixin.common.R;
import com.pangruixin.domain.Role;
import com.pangruixin.domain.User;
import com.pangruixin.domain.UserRole;
import com.pangruixin.domain.dto.UserManageDto;
import com.pangruixin.domain.dto.UserSearchDto;
import com.pangruixin.mapper.UserRoleMapper;
import com.pangruixin.service.RoleService;
import com.pangruixin.service.UserRoleService;
import com.pangruixin.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRoleService userRoleService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Value("${password.key}")
    private String passwordKey;

    @GetMapping("/page")
    @SaCheckPermission("system:user:list")
    public R getUserPage(UserSearchDto userSearchDto) {
        // 用户管理页按手机号/昵称/状态分页查询，只返回后台展示所需字段。
        // pageNum/pageSize 由后台表格组件传入。
        Page<User> page = new Page<>(userSearchDto.getPageNum(), userSearchDto.getPageSize());
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(userSearchDto.getNickName()), User::getNickName, userSearchDto.getNickName())
                .like(StringUtils.hasText(userSearchDto.getPhone()), User::getPhone, userSearchDto.getPhone())
                .eq(userSearchDto.getEnable() != null, User::getEnable, userSearchDto.getEnable())
                .select(User::getId, User::getPhone, User::getSex, User::getAvatar, User::getNickName,
                        User::getLocation, User::getEnable, User::getCreateBy, User::getUpdateBy,
                        User::getCreateTime, User::getUpdateTime)
                // 后台列表默认按最近更新时间倒序显示。
                .orderByDesc(User::getUpdateTime);

        Page<User> result = userService.page(page, wrapper);
        Map<String, Object> map = new HashMap<>();
        // 后台列表统一吃 `{ data, total }` 结构。
        map.put("data", result.getRecords());
        map.put("total", result.getTotal());
        return R.success(map);
    }

    @GetMapping("/{id}")
    @SaCheckPermission("system:user:select")
    public R getUserById(@PathVariable Long id) {
        // 详情接口用于编辑回显，同样不返回密码等敏感字段。
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getId, id)
                .select(User::getId, User::getPhone, User::getSex, User::getAvatar, User::getNickName,
                        User::getLocation, User::getEnable, User::getCreateBy, User::getUpdateBy,
                        User::getCreateTime, User::getUpdateTime);
        User user = userService.getOne(wrapper);
        if (user == null) {
            return R.error("用户不存在");
        }
        return R.success(user);
    }

    @PostMapping
    @SaCheckPermission("system:user:add")
    public R addUser(@RequestBody UserManageDto dto) {
        // 新增用户先做参数校验，再做手机号唯一性校验，最后统一构造 User 实体。
        R validateResult = validateUserDto(dto, false);
        if (validateResult != null) {
            return validateResult;
        }

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getPhone, dto.getPhone()).last("limit 1");
        if (userService.getOne(wrapper) != null) {
            return R.error("该手机号已存在");
        }

        User user = buildUserFromDto(new User(), dto, true);
        // 新增时 forcePassword=true，保证一定会把明文密码加密后写入数据库。
        boolean save = userService.save(user);
        return save ? R.success("新增成功") : R.error("新增失败");
    }

    @PutMapping
    @SaCheckPermission("system:user:update")
    public R updateUser(@RequestBody UserManageDto dto) {
        // 更新用户允许不改密码，但仍需保证手机号在其他用户中唯一。
        if (dto.getId() == null) {
            return R.error("用户ID不能为空");
        }
        R validateResult = validateUserDto(dto, true);
        if (validateResult != null) {
            return validateResult;
        }

        User dbUser = userService.getById(dto.getId());
        if (dbUser == null) {
            return R.error("用户不存在");
        }

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getPhone, dto.getPhone())
                .ne(User::getId, dto.getId())
                .last("limit 1");
        if (userService.getOne(wrapper) != null) {
            return R.error("该手机号已存在");
        }

        User user = buildUserFromDto(dbUser, dto, false);
        // 更新时沿用原实体，只覆盖允许编辑的字段。
        boolean update = userService.updateById(user);
        return update ? R.success("修改成功") : R.error("修改失败");
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("system:user:delete")
    public R deleteUser(@PathVariable Long id) {
        // 删除前先确认用户是否存在，避免前端列表过期导致误删提示不明确。
        User user = userService.getById(id);
        if (user == null) {
            return R.error("用户不存在");
        }
        boolean remove = userService.removeById(id);
        return remove ? R.success("删除成功") : R.error("删除失败");
    }

    @GetMapping("/{userId}/roles")
    @SaCheckPermission("system:user:select")
    public R getUserRoles(@PathVariable Long userId) {
        List<Long> roleIds = userRoleService.getRoleIdsByUserId(userId);
        if (roleIds == null || roleIds.isEmpty()) {
            return R.success(List.of());
        }
        List<Role> roles = roleService.listByIds(roleIds);
        return R.success(roles);
    }

    @PutMapping("/{userId}/roles")
    @SaCheckPermission("system:user:update")
    @Transactional
    public R saveUserRoles(@PathVariable Long userId, @RequestBody List<Long> roleIds) {
        LambdaQueryWrapper<UserRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserRole::getUserId, userId);
        userRoleMapper.delete(wrapper);
        if (roleIds != null) {
            for (Long roleId : roleIds) {
                if (roleId != null) {
                    userRoleMapper.addUserRole(new UserRole(userId, roleId));
                }
            }
        }
        return R.success("角色分配成功");
    }

    private R validateUserDto(UserManageDto dto, boolean isUpdate) {
        // 后端校验与前端表单校验互为补充，保证直接调接口时也能拦截非法参数。
        // isUpdate=false 表示新增，必须要求 password 必填；更新允许不改密码。
        if (!StringUtils.hasText(dto.getPhone())) {
            return R.error("手机号不能为空");
        }
        if (!dto.getPhone().matches("^(13[0-9]|14[01456879]|15[0-35-9]|16[2567]|17[0-8]|18[0-9]|19[0-35-9])\\d{8}$")) {
            return R.error("手机号格式不正确");
        }
        if (!isUpdate && !StringUtils.hasText(dto.getPassword())) {
            return R.error("密码不能为空");
        }
        if (StringUtils.hasText(dto.getPassword()) && !dto.getPassword().matches("^[a-zA-Z]\\w{5,17}$")) {
            return R.error("密码以字母开头,长度在6~18之间,只能包含字母、数字和下划线");
        }
        if (dto.getSex() == null || (dto.getSex() != 0 && dto.getSex() != 1)) {
            return R.error("性别参数不正确");
        }
        if (!StringUtils.hasText(dto.getLocation())) {
            return R.error("所属地区不能为空");
        }
        if (dto.getEnable() == null || (dto.getEnable() != 1 && dto.getEnable() != 2)) {
            return R.error("用户状态参数不正确");
        }
        return null;
    }

    private User buildUserFromDto(User user, UserManageDto dto, boolean forcePassword) {
        // 统一在这里做 DTO -> Entity 映射，避免新增和修改逻辑重复。
        user.setPhone(dto.getPhone());
        user.setSex(dto.getSex());
        user.setAvatar(dto.getAvatar());
        user.setLocation(dto.getLocation());
        user.setEnable(dto.getEnable());
        // 昵称为空时按手机号后四位生成默认昵称。
        user.setNickName(buildNickName(dto.getNickName(), dto.getPhone()));
        if (forcePassword || StringUtils.hasText(dto.getPassword())) {
            // 密码始终以 AES 加密形式存库，与登录解密逻辑保持一致。
            user.setPassword(SaSecureUtil.aesEncrypt(passwordKey, dto.getPassword()));
        }
        return user;
    }

    private String buildNickName(String nickName, String phone) {
        if (StringUtils.hasText(nickName)) {
            return nickName.trim();
        }
        if (!StringUtils.hasText(phone)) {
            return "用户";
        }
        return "用户" + phone.substring(Math.max(0, phone.length() - 4));
    }
}
