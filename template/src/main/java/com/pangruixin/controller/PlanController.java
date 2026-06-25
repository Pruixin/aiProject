package com.pangruixin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pangruixin.common.R;
import com.pangruixin.domain.FitnessPlan;
import com.pangruixin.domain.PlanCheckIn;
import com.pangruixin.domain.User;
import com.pangruixin.exception.BusinessException;
import com.pangruixin.service.FitnessPlanService;
import com.pangruixin.service.PlanCheckInService;
import com.pangruixin.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import cn.dev33.satoken.stp.StpUtil;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/chart/plan")
public class PlanController {

    // Java 后端本身不生成计划，而是把标准化后的用户参数转发给 Python AI 服务。
    private static final String PYTHON_PLAN_URL = "http://localhost:8000/getPlan";

    @Autowired
    private FitnessPlanService fitnessPlanService;
    @Autowired
    private UserService  userService;
    @Autowired
    private PlanCheckInService planCheckInService;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    @PostMapping(value = "/doPlanStream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @SaCheckPermission("portal:plan:generate")
    public SseEmitter doPlanStream(@RequestBody FitnessPlan fitnessPlan) {
        // 流式接口依赖当前登录用户，userId 会写入最终保存的方案记录。
        Long userId = StpUtil.getLoginIdAsLong();
        // 这里补齐当前用户信息，主要是为了把 sex 一并传给 Python 服务。
        User user = userService.getById(userId);
        // 参数校验在最前面执行，尽早阻断非法请求。
        validateFitnessPlan(fitnessPlan);
        if (user == null) {
            throw new BusinessException(0, "当前登录用户不存在");
        }

        // 超时时间传 0 表示不限制，由我们自己控制完成时机。
        SseEmitter emitter = new SseEmitter(0L);
        // 一旦超时，直接结束连接，避免资源泄漏。
        emitter.onTimeout(emitter::complete);

        // 流式接口在独立线程中执行，避免长时间阻塞 Servlet 主线程。
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                // 与 Python 服务建立原生 HTTP 连接。
                URL url = new URL(PYTHON_PLAN_URL);
                conn = (HttpURLConnection) url.openConnection();
                // 这里走 POST，把用户表单作为 JSON 提交给 Python。
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                // 连接超时控制“能否连上服务”。
                conn.setConnectTimeout(5000);
                // 读取超时控制“Python 返回过慢”的情况。
                conn.setReadTimeout(120000);
                // POST 请求需要开启输出流。
                conn.setDoOutput(true);

                try (OutputStream os = conn.getOutputStream()) {
                    // 把前端表单 + 当前用户 sex 拼成 Python 所需 JSON。
                    os.write(buildPythonPayload(fitnessPlan, user).getBytes(StandardCharsets.UTF_8));
                    // 立即刷出请求体。
                    os.flush();
                }

                int responseCode = conn.getResponseCode();
                if (responseCode < 200 || responseCode >= 300) {
                    // Python 返回异常状态时，直接推送 error 事件给前端。
                    safeSendEvent(emitter, "error", null, readResponseBody(conn.getErrorStream(), "AI 计划服务暂时不可用"));
                    emitter.complete();
                    return;
                }

                // 读取 Python 返回的文本流。
                InputStream inputStream = conn.getInputStream();
                byte[] buffer = new byte[1024];
                int len;
                // fullContent 用来累积完整结果，最终还要落库。
                StringBuilder fullContent = new StringBuilder();
                while ((len = inputStream.read(buffer)) != -1) {
                    // Python 返回一段，这里就立刻转成一个 chunk 事件推给前端，
                    // 前端据此实时拼接和展示半成品计划。
                    String chunk = new String(buffer, 0, len, StandardCharsets.UTF_8);
                    fullContent.append(chunk);
                    safeSendEvent(emitter, "chunk", chunk, null);
                }

                // 流式展示结束后，仍需把完整结果清洗并持久化，方便后台管理端查看历史方案。
                String finalResult = cleanPlanResult(fullContent.toString());
                fitnessPlan.setPlanData(parsePlanData(finalResult));
                fitnessPlan.setUserId(userId);
                fitnessPlan.setCreateTime(LocalDateTime.now());
                // 流式接口同样会把最终生成结果保存到数据库。
                fitnessPlanService.save(fitnessPlan);
                safeSendEvent(emitter, "done", null, null, fitnessPlan.getId());
                emitter.complete();
            } catch (BusinessException e) {
                // 业务异常透传给前端。
                safeSendEvent(emitter, "error", null, e.getMessage());
                emitter.complete();
            } catch (Exception e) {
                // 其他异常统一转换成更友好的提示。
                safeSendEvent(emitter, "error", null, resolvePlanErrorMessage(e));
                emitter.complete();
            } finally {
                if (conn != null) {
                    // 无论成功还是失败，都关闭底层 HTTP 连接。
                    conn.disconnect();
                }
            }
        }).start();

        // 立即把 emitter 返回给前端，后续数据由后台线程持续推送。
        return emitter;
    }

    @GetMapping("/history")
    @SaCheckPermission("portal:plan:generate")
    public R listHistory() {
        Long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<FitnessPlan> wrapper = new LambdaQueryWrapper<FitnessPlan>()
                .eq(FitnessPlan::getUserId, userId)
                .select(FitnessPlan::getId, FitnessPlan::getHeight, FitnessPlan::getWeight,
                        FitnessPlan::getGoal, FitnessPlan::getHeartDisease, FitnessPlan::getCreateTime)
                .orderByDesc(FitnessPlan::getCreateTime);
        List<FitnessPlan> plans = fitnessPlanService.list(wrapper);
        List<Map<String, Object>> records = plans.stream().map(plan -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", plan.getId());
            row.put("height", plan.getHeight());
            row.put("weight", plan.getWeight());
            row.put("goal", plan.getGoal());
            row.put("heartDisease", plan.getHeartDisease());
            row.put("createTime", plan.getCreateTime());
            return row;
        }).toList();
        return R.success(records);
    }

    @GetMapping("/history/{id}")
    @SaCheckPermission("portal:plan:generate")
    public R getHistoryDetail(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        FitnessPlan plan = fitnessPlanService.getById(id);
        if (plan == null) {
            return R.error("方案不存在");
        }
        if (!userId.equals(plan.getUserId())) {
            return R.error("无权查看该方案");
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", plan.getId());
        row.put("height", plan.getHeight());
        row.put("weight", plan.getWeight());
        row.put("goal", plan.getGoal());
        row.put("heartDisease", plan.getHeartDisease());
        row.put("createTime", plan.getCreateTime());
        row.put("planData", plan.getPlanData());
        return R.success(row);
    }

    @PostMapping("/checkin")
    @SaCheckPermission("portal:plan:generate")
    public R checkin(@RequestParam("file") MultipartFile file,
                     @RequestParam("planId") Long planId,
                     @RequestParam("dayIndex") Integer dayIndex) throws IOException {
        Long userId = StpUtil.getLoginIdAsLong();
        if (file == null || file.isEmpty()) {
            return R.error("请先选择图片");
        }
        FitnessPlan plan = fitnessPlanService.getById(planId);
        if (plan == null || !userId.equals(plan.getUserId())) {
            return R.error("方案不存在或无权操作");
        }
        if (dayIndex == null || dayIndex < 0 || dayIndex > 6) {
            return R.error("天数参数不合法");
        }
        String originalFilename = file.getOriginalFilename();
        String extension = StringUtils.getFilenameExtension(originalFilename);
        String normalizedExtension = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        String contentType = file.getContentType();
        boolean imageContentType = StringUtils.hasText(contentType) && contentType.toLowerCase(Locale.ROOT).startsWith("image/");
        if (!ALLOWED_IMAGE_EXTENSIONS.contains(normalizedExtension) || !imageContentType) {
            return R.error("仅支持上传 jpg、png、webp、gif 图片");
        }
        String fileName = UUID.randomUUID().toString().replace("-", "");
        if (StringUtils.hasText(normalizedExtension)) {
            fileName = fileName + "." + normalizedExtension;
        }
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadPath);
        Path targetFile = uploadPath.resolve(fileName);
        Files.copy(file.getInputStream(), targetFile, StandardCopyOption.REPLACE_EXISTING);

        PlanCheckIn checkIn = new PlanCheckIn();
        checkIn.setPlanId(planId);
        checkIn.setUserId(userId);
        checkIn.setDayIndex(dayIndex);
        checkIn.setImageUrl("/uploads/" + fileName);
        checkIn.setCreateTime(LocalDateTime.now());
        planCheckInService.save(checkIn);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", checkIn.getId());
        result.put("imageUrl", checkIn.getImageUrl());
        result.put("dayIndex", checkIn.getDayIndex());
        result.put("createTime", checkIn.getCreateTime());
        return R.success(result);
    }

    @GetMapping("/checkin/{planId}")
    @SaCheckPermission("portal:plan:generate")
    public R listCheckin(@PathVariable Long planId) {
        Long userId = StpUtil.getLoginIdAsLong();
        FitnessPlan plan = fitnessPlanService.getById(planId);
        if (plan == null || !userId.equals(plan.getUserId())) {
            return R.error("方案不存在或无权查看");
        }
        LambdaQueryWrapper<PlanCheckIn> wrapper = new LambdaQueryWrapper<PlanCheckIn>()
                .eq(PlanCheckIn::getPlanId, planId)
                .orderByAsc(PlanCheckIn::getDayIndex)
                .orderByDesc(PlanCheckIn::getCreateTime);
        List<PlanCheckIn> checkIns = planCheckInService.list(wrapper);
        return R.success(checkIns);
    }

    private void validateFitnessPlan(FitnessPlan fitnessPlan) {
        // 这里的校验与前端表单校验互为补充，确保绕过前端时后端仍能拦截非法请求。
        if (fitnessPlan == null) {
            throw new BusinessException(0, "请求参数不能为空");
        }
        if (!isPositiveNumber(fitnessPlan.getHeight())) {
            throw new BusinessException(0, "身高不能为空且必须为数字");
        }
        if (!isPositiveNumber(fitnessPlan.getWeight())) {
            throw new BusinessException(0, "体重不能为空且必须为数字");
        }
        if (!StringUtils.hasText(fitnessPlan.getGoal())) {
            throw new BusinessException(0, "健身目标不能为空");
        }
    }

    private boolean isPositiveNumber(String value) {
        // 空字符串或 null 直接判定为非法。
        if (!StringUtils.hasText(value)) {
            return false;
        }
        try {
            // 只允许大于 0 的数字。
            return Double.parseDouble(value.trim()) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String buildPythonPayload(FitnessPlan fitnessPlan, User user) {
        // Python 服务使用固定字段结构，这里把登录用户性别与表单参数一起下发。
        return "{\"height\":\"" + fitnessPlan.getHeight().trim()
                + "\",\"weight\":\"" + fitnessPlan.getWeight().trim()
                + "\",\"sex\":\"" + (user.getSex() == null ? "" : user.getSex())
                + "\",\"die_of_illness\":\"" + safeText(fitnessPlan.getHeartDisease())
                + "\",\"goal\":\"" + safeText(fitnessPlan.getGoal().trim()) + "\"}";
    }

    private String safeText(String value) {
        // 手动转义反斜杠和双引号，避免拼接 JSON 时破坏结构。
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String cleanPlanResult(String result) {
        // AI 返回结果有时会被 ```json 包裹，保存和二次解析前先去掉 markdown 外壳。
        String cleanResult = result == null ? "" : result.trim();
        if (cleanResult.contains("```json")) {
            cleanResult = cleanResult.substring(cleanResult.indexOf("```json") + 7);
            if (cleanResult.contains("```")) {
                cleanResult = cleanResult.substring(0, cleanResult.lastIndexOf("```"));
            }
        } else if (cleanResult.contains("```")) {
            cleanResult = cleanResult.substring(cleanResult.indexOf("```") + 3);
            if (cleanResult.contains("```")) {
                cleanResult = cleanResult.substring(0, cleanResult.lastIndexOf("```"));
            }
        }
        return cleanResult.trim();
    }

    private JSONObject parsePlanData(String result) {
        String current = cleanPlanResult(result);
        for (int i = 0; i < 4 && StringUtils.hasText(current); i++) {
            try {
                Object parsed = JSON.parse(current);
                if (parsed instanceof JSONObject jsonObject) {
                    String errorMessage = extractPlanErrorMessage(jsonObject);
                    if (StringUtils.hasText(errorMessage)) {
                        throw new BusinessException(0, errorMessage);
                    }
                    return jsonObject;
                }
                if (parsed instanceof String nested) {
                    current = cleanPlanResult(nested);
                    continue;
                }
            } catch (Exception ignored) {
                // 先尝试下一轮“解包字符串 JSON”的容错处理。
            }
            String repaired = unwrapQuotedJsonString(current);
            if (current.equals(repaired)) {
                break;
            }
            current = repaired;
        }
        throw new BusinessException(0, "AI 计划结果解析失败");
    }

    private String extractPlanErrorMessage(JSONObject jsonObject) {
        if (jsonObject == null) {
            return "";
        }
        Object codeObj = jsonObject.get("code");
        if (codeObj instanceof Number) {
            int code = ((Number) codeObj).intValue();
            if (code >= 400) {
                return "AI 服务返回错误码: " + code;
            }
        }
        Object message = jsonObject.get("error");
        if (!(message instanceof String) || !StringUtils.hasText((String) message)) {
            message = jsonObject.get("message");
        }
        return message instanceof String ? ((String) message).trim() : "";
    }

    private String unwrapQuotedJsonString(String value) {
        String text = cleanPlanResult(value);
        if (text.startsWith("\"")) {
            text = text.substring(1);
        }
        if (text.endsWith("\"")) {
            text = text.substring(0, text.length() - 1);
        }
        return text
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "")
                .replace("\\t", "\t")
                .replace("\\\\", "\\")
                .trim();
    }

    private String readResponseBody(InputStream inputStream, String defaultMessage) throws IOException {
        if (inputStream == null) {
            return defaultMessage;
        }
        // 直接一次性读完整个输入流，适合同步错误信息或最终完整结果。
        byte[] bytes = inputStream.readAllBytes();
        String result = new String(bytes, StandardCharsets.UTF_8).trim();
        return StringUtils.hasText(result) ? result : defaultMessage;
    }

    private void safeSendEvent(SseEmitter emitter, String type, String content, String message) {
        safeSendEvent(emitter, type, content, message, null);
    }

    private void safeSendEvent(SseEmitter emitter, String type, String content, String message, Long planId) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", type);
            if (content != null) {
                payload.put("content", content);
            }
            if (message != null) {
                payload.put("message", message);
            }
            if (planId != null) {
                payload.put("planId", planId);
            }
            emitter.send(SseEmitter.event().data(JSON.toJSONString(payload)));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private String resolvePlanErrorMessage(Exception e) {
        // 优先使用底层异常本身的信息，否则返回通用提示。
        String message = e.getMessage();
        if (StringUtils.hasText(message)) {
            return message;
        }
        return "生成健身计划失败，请稍后重试";
    }
}
