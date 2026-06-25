package com.pangruixin.domain.dto;

import lombok.Data;

@Data
/**
 * 饮食计划七天的VO类
 */
public class DietMotionPlanVO {
    private OneDayPlan oneDay;
    private OneDayPlan towDay;
    private OneDayPlan threeDay;
    private OneDayPlan fourDay;
    private OneDayPlan fiveDay;
    private OneDayPlan sixDay;
    private OneDayPlan seven;
}