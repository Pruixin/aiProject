package com.pangruixin.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pangruixin.domain.FitnessPlan;
import com.pangruixin.domain.User;
import com.pangruixin.exception.BusinessException;
import com.pangruixin.mapper.FitnessPlanMapper;
import com.pangruixin.service.FitnessPlanService;
import com.pangruixin.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

@Service
public class FitnessPlanServiceImpl extends ServiceImpl<FitnessPlanMapper, FitnessPlan> implements FitnessPlanService {

    // 同步生成链路直接调用 Python 服务获取完整计划结果。
    private static final String PYTHON_PLAN_URL = "http://localhost:8000/getPlan";

    @Autowired
    private FitnessPlanMapper fitnessPlanMapper;

    @Autowired
    private UserService userService;

    @Override
    public Object doPlan(FitnessPlan fitnessPlan) {
        // 先校验请求，再根据登录态补齐性别等 AI 生成所需上下文。
        validateFitnessPlan(fitnessPlan);
        // 当前登录用户 id 会作为方案归属用户写入数据库。
        Long userId = StpUtil.getLoginIdAsLong();
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(0, "当前登录用户不存在");
        }

        HttpURLConnection conn = null;
        try {
            // 建立到 Python AI 服务的 HTTP 连接。
            URL url = new URL(PYTHON_PLAN_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            // 连接阶段超时。
            conn.setConnectTimeout(5000);
            // 读取阶段超时。
            conn.setReadTimeout(120000);
            // POST 请求需要允许写入请求体。
            conn.setDoOutput(true);

            try (OutputStream stream = conn.getOutputStream()) {
                // 写入发送给 Python 的标准 JSON。
                stream.write(buildPythonPayload(fitnessPlan, user).getBytes(StandardCharsets.UTF_8));
                stream.flush();
            }

            int responseCode = conn.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                // Python 侧如果出错，优先把错误流中的文本返回给前端。
                throw new BusinessException(0, readResponseBody(conn.getErrorStream(), "AI 计划服务暂时不可用"));
            }

            // 先把 AI 原始文本清洗成纯 JSON，再写入数据库和返回前端。
            String cleanResult = cleanPlanResult(readResponseBody(conn.getInputStream(), "AI 计划服务返回空数据"));
            try {
                // 保存时数据库里存的是结构化 JSON，而不是原始文本。
                fitnessPlan.setPlanData(parsePlanData(cleanResult));
            } catch (Exception e) {
                throw new BusinessException(0, "AI 计划结果解析失败");
            }
            fitnessPlan.setUserId(userId);
            // 同步接口生成成功后立即落库。
            fitnessPlanMapper.insert(fitnessPlan);
            // 返回给前端的是可直接使用的 JSON 对象。
            return JSON.parse(cleanResult);
        } catch (BusinessException e) {
            // 业务异常原样抛出，交给全局异常处理器统一包装。
            throw e;
        } catch (IOException e) {
            throw new BusinessException(0, "AI 计划服务连接失败，请检查 Python 服务是否已启动");
        } catch (Exception e) {
            throw new BusinessException(0, "生成健身计划失败，请稍后重试");
        } finally {
            if (conn != null) {
                // 释放底层 HTTP 连接资源。
                conn.disconnect();
            }
        }
    }

    private void validateFitnessPlan(FitnessPlan fitnessPlan) {
        // 后端参数校验保证同步接口和流式接口的约束一致。
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
        if (!StringUtils.hasText(value)) {
            return false;
        }
        try {
            // 去掉首尾空格后再转数字，避免用户输入 `" 170 "` 这种情况。
            return Double.parseDouble(value.trim()) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String buildPythonPayload(FitnessPlan fitnessPlan, User user) {
        // Python 服务依赖字符串字段，这里统一做 trim 和转义处理。
        return "{\"height\":\"" + fitnessPlan.getHeight().trim()
                + "\",\"weight\":\"" + fitnessPlan.getWeight().trim()
                + "\",\"sex\":\"" + (user.getSex() == null ? "" : user.getSex())
                + "\",\"die_of_illness\":\"" + safeText(fitnessPlan.getHeartDisease())
                + "\",\"goal\":\"" + safeText(fitnessPlan.getGoal().trim()) + "\"}";
    }

    private String safeText(String value) {
        // 手动对字符串中的转义字符做最小必要处理，保证拼接出的 JSON 合法。
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String cleanPlanResult(String result) {
        // 兼容 AI 把 JSON 包在 markdown 代码块中的情况。
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
                // 继续尝试把被整体包成字符串的 JSON 解开。
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
        try (Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8)) {
            // `\\Z` 匹配输入结束位置，用于一次性读取完整流内容。
            String result = scanner.useDelimiter("\\Z").hasNext() ? scanner.next() : "";
            return StringUtils.hasText(result) ? result.trim() : defaultMessage;
        }
    }
}
