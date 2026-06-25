package com.pangruixin.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("plan_checkin")
public class PlanCheckIn {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long planId;

    private Long userId;

    private Integer dayIndex;

    private String imageUrl;

    private LocalDateTime createTime;
}