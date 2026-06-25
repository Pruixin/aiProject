package com.pangruixin.common;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class MyAutoFieldFill implements MetaObjectHandler {

    //当mybatis执行插入的时候调用此方法
    @Override
    public void insertFill(MetaObject metaObject) {
        //指定字段填充值
        //让mp框架自动填充createTime字段
        setFieldValByName("createTime", LocalDateTime.now(), metaObject);
        setFieldValByName("updateTime", LocalDateTime.now(), metaObject);
        
        try {
            // 尝试在用户已登录的情况下填充创建人字段
            if(StpUtil.isLogin()) {
                setFieldValByName("createBy", StpUtil.getLoginIdAsLong(), metaObject);
                setFieldValByName("updateBy", StpUtil.getLoginIdAsLong(), metaObject);
            }
        } catch (Throwable e) {
            // 在子线程（异步线程）中无法读取 Sa-Token 上下文，捕获所有 Throwable（包括 SaTokenContextException）
        }
        setFieldValByName("orderTime", LocalDateTime.now(), metaObject);
    }
    //当mybatis执行更新的时候调用此方法
    @Override
    public void updateFill(MetaObject metaObject) {
        setFieldValByName("updateTime", LocalDateTime.now(), metaObject);
        
        try {
            // 尝试在用户已登录的情况下填充更新人字段
            if(StpUtil.isLogin()) {
                setFieldValByName("updateBy", StpUtil.getLoginIdAsLong(), metaObject);
            }
        } catch (Throwable e) {
            // 同上
        }
        setFieldValByName("checkoutTime", LocalDateTime.now(), metaObject);
    }
}
