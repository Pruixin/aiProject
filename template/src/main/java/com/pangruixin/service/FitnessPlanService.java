package com.pangruixin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.pangruixin.domain.FitnessPlan;

public interface FitnessPlanService extends IService<FitnessPlan> {
    Object doPlan(FitnessPlan fitnessPlan);
}
