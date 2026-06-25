package com.pangruixin.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.jwt.StpLogicJwtForSimple;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class SaTokenConfigure  implements WebMvcConfigurer {
    @Value("${spring.sa-token.jwt-secret-key}")
    private String jwtMY;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Override
    public void addInterceptors(InterceptorRegistry registry){
            // 同时打开两类能力：
            // 1. 路由级全局登录校验；
            // 2. 控制器上的 @SaCheckRole / @SaCheckPermission 注解鉴权。
            registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()).isAnnotation(true)
                    )
                    .addPathPatterns("/**")
                    .excludePathPatterns("/auth/**", "/dict/**", "/common/**", "/uploads/**", "/ws/**");

    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadPath.toUri().toString());
    }

    @Bean
    public StpLogic getStpLogicJwt() {
        return new StpLogicJwtForSimple() {
            @Override
            public String jwtSecretKey() {
                return jwtMY;
            }
        };
    }
}
