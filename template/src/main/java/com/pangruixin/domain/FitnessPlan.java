package com.pangruixin.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.extension.handlers.FastjsonTypeHandler;

@Data
@TableName(value = "fitness_plan", autoResultMap = true)
public class FitnessPlan {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String height; // 身高
    private String weight; // 体重
    private String heartDisease; // 心脏病
    private String goal; // 目标

    // 完整7天计划 JSON 字符串，配置自动转换器
    @TableField(typeHandler = FastjsonTypeHandler.class)
    private JSONObject planData;

    private LocalDateTime createTime;

    @TableLogic
    protected int deleted;
}