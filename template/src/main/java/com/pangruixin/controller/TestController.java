package com.pangruixin.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.pangruixin.common.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class TestController {
    @SaCheckRole("superadmin")
    @GetMapping("/test1")
    public R test1(){
        return R.success("访问成功");
    }
}
