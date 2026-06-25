package com.pangruixin.domain.dto;

import lombok.Data;
import java.util.List;

@Data
/**
 * 运动计划数据VO类
 */

public class Motion {
    private String motionContent;
    private List<String> action;
    private String aerobic;
    private String duration;
}