package com.pangruixin.controller;

import cn.dev33.satoken.secure.SaSecureUtil;
import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pangruixin.common.R;
import com.pangruixin.common.tree.TreeUtil;
import com.pangruixin.domain.Menu;
import com.pangruixin.domain.Role;
import com.pangruixin.domain.SocialProfile;
import com.pangruixin.domain.User;
import com.pangruixin.domain.UserRole;
import com.pangruixin.mapper.SocialProfileMapper;
import com.pangruixin.service.MenuService;
import com.pangruixin.service.RoleService;
import com.pangruixin.service.UserService;
import com.pangruixin.service.UserRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/auth")
@Validated
public class AuthController {
    @Autowired
    private UserService userService;

    @Autowired
    private SocialProfileMapper socialProfileMapper;

    @Autowired
    private RoleService roleService;

    @Autowired
    private MenuService menuService;


    @Autowired
    private UserRoleService userRoleService;

    @Value("${password.key}")
    private String passwordKey;

    @Value("${auth.default-role-key:users}")
    private String defaultRoleKey;



    @PostMapping("/login")
    public R login(@RequestBody @Validated({User.First.class}) User user){
        // 登录时只查询鉴权所需字段，避免把整条用户信息全部捞出来。
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getPhone,user.getPhone())
                .select(User::getPassword,User::getId, User::getEnable);
        // 手机号作为当前系统登录主键。
        User one = userService.getOne(wrapper);
        if (one == null){
            return R.error("手机号不正确");
        }
        // enable=1 表示可登录，其它值统一视为禁用。
        if (!Objects.equals(one.getEnable(), 1)) {
            return R.error("当前账号已被禁用");
        }
        // 数据库存的是 AES 密文，这里先解密，再和前端传来的明文密码比较。
        String aesDecrypt = SaSecureUtil.aesDecrypt(passwordKey, one.getPassword());

        if(aesDecrypt.trim().equals(user.getPassword())){
            // Sa-Token 登录成功后会把当前用户 id 绑定到 token/session。
            StpUtil.login(one.getId());
            // tokenInfo 里包含 tokenName、tokenValue 等前端初始化所需信息。
            SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
            return R.success(tokenInfo);
        }
        return R.error("手机号或密码错误");
    }

    @PostMapping("/register")
    @Transactional(rollbackFor = Exception.class)
    public R register(@RequestBody @Validated(User.Second.class) User user){
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        // 一个手机号只能注册一个账号。
        wrapper.eq(User::getPhone, user.getPhone()).last("limit 1");
        if (userService.getOne(wrapper) != null) {
            return R.error("该手机号已注册");
        }

        // 新注册用户默认启用。
        if (user.getEnable() == null || user.getEnable() == 0) {
            user.setEnable(1);
        }
        // 没填昵称时，用手机号后四位生成默认昵称，降低注册门槛。
        if (user.getNickName() == null || user.getNickName().trim().isEmpty()) {
            String phone = user.getPhone();
            user.setNickName("用户" + phone.substring(Math.max(0, phone.length() - 4)));
        }

        // 注册入库前统一加密密码，保持与登录解密逻辑对应。
        String aesEncrypt = SaSecureUtil.aesEncrypt(passwordKey, user.getPassword());
        user.setPassword(aesEncrypt);

        // 先保存 user 主表，拿到自增 id 后再建立其它关联数据。
        boolean save = userService.save(user);
        if (save){
            // 注册不仅创建 user，还要补齐“角色关系 + 社交资料”两份初始化数据。
            Role defaultRole = resolveDefaultRegisterRole();
            if (defaultRole == null) {
                return R.error("未找到可分配的普通用户角色，请先在后台创建普通用户角色");
            }
            boolean bindRoleSuccess = userRoleService.addUserRole(new UserRole(user.getId(), defaultRole.getId()));
            if (!bindRoleSuccess) {
                return R.error("注册失败，默认角色分配失败");
            }
            SocialProfile profile = new SocialProfile();
            profile.setUserId(user.getId());
            // 社交目标直接沿用注册表单里填写的目标，减少用户首次进入社交页的补录成本。
            profile.setGoal(user.getSocialGoal());
            // 初始简介为空，后续由用户自己在社交资料页补充。
            profile.setIntro("");
            // 1 表示资料启用。
            profile.setStatus(1);
            socialProfileMapper.insert(profile);
            return R.success("注册成功");
        }
        return R.error("注册失败");

    }

    @GetMapping("/getInfo")
    public R getInfo(){
        boolean login = StpUtil.isLogin();
        if (!login){
            return R.error("请先登录", 401);
        }
        Long loginUserId = StpUtil.getLoginIdAsLong();
        HashMap<String, Object> map = new HashMap<>();
        // 管理端初始化依赖这里一次性返回角色、菜单树和当前用户基础信息。
        // roleList 既能给前端做显示，也能参与某些管理员兜底逻辑判断。
        List<String> roleList = StpUtil.getRoleList(StpUtil.getLoginId());
        // getRouterList 取到的是“当前用户可见菜单”，再做一次本项目兼容性规整。
        List<Menu> menuList = normalizeAdminMenus(menuService.getRouterList(loginUserId), roleList);
        // 按钮权限不在路由树里，前端做按钮显隐时需要单独拿一份完整权限码列表。
        List<String> permissionList = menuService.getUserMenusList(loginUserId).stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getId,loginUserId)
                // 管理端顶栏目前只需要手机号、头像、昵称、性别这些轻量字段。
                .select(User::getPhone,User::getAvatar,User::getNickName,User::getSex);
        User user = userService.getOne(wrapper);

        // 后端先把菜单规整成树，再由管理端据此动态注册路由和渲染侧边栏。
        List<Menu> menus = TreeUtil.makeTree(
                menuList,
                x -> x.getParentId() == 0,
                (parent, child) -> parent.getId().equals(child.getParentId()),
                Menu::setChildren
        );
        map.put("roleList",roleList);
        map.put("menus",menus);
        map.put("permissionList", permissionList);
        map.put("user",user);
        return R.success(map);

    }

    @GetMapping("/profile")
    public R getProfile() {
        Long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getId, userId)
                .select(User::getPhone, User::getSex, User::getLocation,
                        User::getAvatar, User::getNickName);
        User user = userService.getOne(wrapper);
        if (user == null) {
            return R.error("用户不存在");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("phone", user.getPhone());
        result.put("sex", user.getSex());
        result.put("location", user.getLocation());
        result.put("avatar", user.getAvatar());
        result.put("nickName", user.getNickName());
        return R.success(result);
    }

    @PutMapping("/profile")
    public R updateProfile(@RequestBody Map<String, Object> body) {
        Long userId = StpUtil.getLoginIdAsLong();
        User user = userService.getById(userId);
        if (user == null) {
            return R.error("用户不存在");
        }
        Object avatar = body.get("avatar");
        if (avatar instanceof String && StringUtils.hasText((String) avatar)) {
            user.setAvatar((String) avatar);
        }
        Object nickName = body.get("nickName");
        if (nickName instanceof String && StringUtils.hasText((String) nickName)) {
            user.setNickName(((String) nickName).trim());
        }
        Object password = body.get("password");
        if (password instanceof String && StringUtils.hasText((String) password)) {
            user.setPassword(SaSecureUtil.aesEncrypt(passwordKey, (String) password));
        }
        userService.updateById(user);
        return R.success("更新成功");
    }


    private Role resolveDefaultRegisterRole() {
        // 注册默认角色优先按配置键匹配，再按常见名称兜底，最后选一个非管理员角色。
        List<Role> activeRoles = roleService.list(new LambdaQueryWrapper<Role>()
                // 当前角色表沿用 0=启用、1=停用 的状态语义，和菜单状态保持一致。
                .eq(Role::getStatus, 0)
                .orderByAsc(Role::getId));
        if (activeRoles.isEmpty()) {
            return null;
        }

        List<String> preferredKeys = Stream.of(defaultRoleKey, "users", "user", "common", "normal", "member")
                .filter(StringUtils::hasText)
                // trim 后再比较，兼容配置里手滑带空格的情况。
                .map(String::trim)
                .distinct()
                .toList();
        for (String roleKey : preferredKeys) {
            Role matched = activeRoles.stream()
                    .filter(role -> roleKey.equalsIgnoreCase(role.getRoleKey()))
                    .findFirst()
                    .orElse(null);
            if (matched != null) {
                return matched;
            }
        }

        List<String> preferredNames = List.of("普通用户", "普通会员", "用户", "会员");
        for (String roleName : preferredNames) {
            // 某些环境里没有 roleKey 规范，就回退按中文名称兜底。
            Role matched = activeRoles.stream()
                    .filter(role -> roleName.equals(role.getName()))
                    .findFirst()
                    .orElse(null);
            if (matched != null) {
                return matched;
            }
        }

        return activeRoles.stream()
                // 实在匹配不到，再选第一个看起来不是管理员的角色。
                .filter(role -> !isAdminRole(role))
                .findFirst()
                .orElse(null);
    }

    private boolean isAdminRole(Role role) {
        // 同时检查 roleKey 和名称，尽量识别各种管理员角色命名方式。
        String roleKey = role.getRoleKey() == null ? "" : role.getRoleKey().toLowerCase();
        String roleName = role.getName() == null ? "" : role.getName();
        return roleKey.contains("admin")
                || roleKey.contains("super")
                || roleKey.contains("root")
                || roleName.contains("管理员");
    }

    private List<Menu> normalizeAdminMenus(List<Menu> menus, List<String> roleList) {
        // 这里对后台菜单做一次兼容性裁剪：
        // 去掉首页和冗余按钮权限，但绝不额外补菜单，避免把未授权页面展示给前端。
        Set<String> redundantUserPerms = Set.of(
                "system:user:add",
                "system:user:update",
                "system:user:delete",
                "system:user:select"
        );
        // 有些历史菜单数据会把“用户管理”的按钮权限也混到路由树里，这里先识别出来。
        Set<Long> userMenuIds = menus.stream()
                .filter(menu -> Objects.equals(menu.getPath(), "user")
                        || Objects.equals(menu.getPerms(), "system:user:list")
                        || Objects.equals(menu.getName(), "用户管理"))
                .map(Menu::getId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        List<Menu> filteredMenus = menus.stream()
                // 首页通常单独处理，不放进动态菜单侧边栏。
                .filter(menu -> !Objects.equals(menu.getPath(), "index"))
                // 纯按钮权限不应出现在菜单树。
                .filter(menu -> !redundantUserPerms.contains(menu.getPerms()))
                // 某些脏数据里按钮会挂在用户管理下面，这里一并裁掉。
                .filter(menu -> !userMenuIds.contains(menu.getParentId()))
                .toList();
        return filteredMenus;
    }
}
