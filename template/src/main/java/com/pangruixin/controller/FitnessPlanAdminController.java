package com.pangruixin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pangruixin.common.R;
import com.pangruixin.domain.FitnessPlan;
import com.pangruixin.domain.User;
import com.pangruixin.domain.dto.FitnessPlanSearchDto;
import com.pangruixin.mapper.FitnessPlanMapper;
import com.pangruixin.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/fitness-plan")
public class FitnessPlanAdminController {

    @Autowired
    private FitnessPlanMapper fitnessPlanMapper;

    @Autowired
    private UserService userService;

    @GetMapping("/page")
    @SaCheckPermission("system:fitness-plan:list")
    public R getFitnessPlanPage(FitnessPlanSearchDto queryDto) {
        // 后台管理页按用户维度检索 AI 计划，列表页只返回摘要字段，不返回完整 planData。
        // 分页参数来自后台表格搜索区。
        Page<FitnessPlan> page = new Page<>(queryDto.getPageNum(), queryDto.getPageSize());
        LambdaQueryWrapper<FitnessPlan> wrapper = new LambdaQueryWrapper<FitnessPlan>()
                .eq(queryDto.getUserId() != null, FitnessPlan::getUserId, queryDto.getUserId())
                .eq(StringUtils.hasText(queryDto.getGoal()), FitnessPlan::getGoal, queryDto.getGoal())
                .select(FitnessPlan::getId, FitnessPlan::getUserId, FitnessPlan::getHeight,
                        FitnessPlan::getWeight, FitnessPlan::getHeartDisease, FitnessPlan::getGoal,
                        FitnessPlan::getCreateTime)
                .orderByDesc(FitnessPlan::getCreateTime);

        // 手机号/昵称是 user 表字段，因此先查出匹配用户 id，再反向过滤方案表。
        Set<Long> filteredUserIds = findUserIdsByKeyword(queryDto.getPhone(), queryDto.getNickName());
        if (filteredUserIds != null && filteredUserIds.isEmpty()) {
            // 有关键词但一个用户都没命中时，直接返回空页，避免继续查方案表。
            return R.success(pageResult(List.of(), 0));
        }
        if (filteredUserIds != null) {
            wrapper.in(FitnessPlan::getUserId, filteredUserIds);
        }

        Page<FitnessPlan> result = fitnessPlanMapper.selectPage(page, wrapper);
        Map<Long, User> userMap = loadUsersByIds(result.getRecords().stream()
                .map(FitnessPlan::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));

        List<Map<String, Object>> records = result.getRecords().stream()
                // 列表页不带 planData，减少响应体体积。
                .map(plan -> buildPlanRow(plan, userMap.get(plan.getUserId()), false))
                .toList();
        return R.success(pageResult(records, result.getTotal()));
    }

    @GetMapping("/{id}")
    @SaCheckPermission("system:fitness-plan:list")
    public R getFitnessPlanById(@PathVariable Long id) {
        // 详情接口才返回完整 planData，给后台查看具体 7 天计划 JSON。
        FitnessPlan plan = fitnessPlanMapper.selectById(id);
        if (plan == null) {
            return R.error("健身方案不存在");
        }
        User user = plan.getUserId() == null ? null : userService.getById(plan.getUserId());
        return R.success(buildPlanRow(plan, user, true));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("system:fitness-plan:list")
    public R deleteFitnessPlan(@PathVariable Long id) {
        // 删除只针对单条历史方案，不影响用户本身数据。
        FitnessPlan plan = fitnessPlanMapper.selectById(id);
        if (plan == null) {
            return R.error("健身方案不存在");
        }
        return fitnessPlanMapper.deleteById(id) > 0 ? R.success("删除成功") : R.error("删除失败");
    }

    private Set<Long> findUserIdsByKeyword(String phone, String nickName) {
        // 根据手机号/昵称模糊匹配出 user.id 集合，用于 fitness_plan 过滤。
        boolean hasPhone = StringUtils.hasText(phone);
        boolean hasNickName = StringUtils.hasText(nickName);
        if (!hasPhone && !hasNickName) {
            return null;
        }
        return userService.list(new LambdaQueryWrapper<User>()
                        .like(hasPhone, User::getPhone, phone)
                        .like(hasNickName, User::getNickName, nickName)
                        .select(User::getId))
                .stream()
                .map(User::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Map<Long, User> loadUsersByIds(Set<Long> userIds) {
        // 批量加载用户，避免列表遍历时逐条查库。
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userService.listByIds(userIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(User::getId, item -> item, (left, right) -> left));
    }

    private Map<String, Object> buildPlanRow(FitnessPlan plan, User user, boolean includePlanData) {
        // 把方案表和用户表拼成一个后台展示用行对象。
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", plan.getId());
        row.put("userId", plan.getUserId());
        row.put("height", plan.getHeight());
        row.put("weight", plan.getWeight());
        row.put("heartDisease", plan.getHeartDisease());
        row.put("goal", plan.getGoal());
        row.put("createTime", plan.getCreateTime());
        row.put("userPhone", user == null ? "" : user.getPhone());
        row.put("userNickName", user == null ? "" : user.getNickName());
        row.put("userSex", user == null ? null : user.getSex());
        if (includePlanData) {
            row.put("planData", plan.getPlanData());
        }
        return row;
    }

    private Map<String, Object> pageResult(List<Map<String, Object>> data, long total) {
        // 与后台其他分页接口保持统一返回结构：{ data, total }。
        Map<String, Object> result = new HashMap<>();
        result.put("data", data);
        result.put("total", total);
        return result;
    }
}
