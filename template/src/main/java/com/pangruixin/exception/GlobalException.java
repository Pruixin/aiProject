package com.pangruixin.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.exception.SaTokenException;
import com.pangruixin.common.Code;
import com.pangruixin.common.R;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalException {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败";
        return R.error(message);
    }

//    自定义异常
    @ExceptionHandler(BusinessException.class)
    public R handleBusinessException(BusinessException e){
        return R.error(e.getMessage());

    }

//    未登录异常
    @ExceptionHandler(NotLoginException.class)
    public R handleNotLoginException(NotLoginException e){
        return R.error(e.getMessage(), 401);
    }


    // 拦截：缺少权限异常
    @ExceptionHandler(NotPermissionException.class)
    public R handlerException(NotPermissionException e) {
        return R.error("缺少权限：" + e.getPermission(), 403);
    }

    // 拦截：缺少角色异常
    @ExceptionHandler(NotRoleException.class)
    public R handlerException(NotRoleException e) {
        return R.error("缺少角色：" + e.getRole(), 403);
    }

    @ExceptionHandler(SaTokenException.class)
    public R handleSaTokenException(SaTokenException e) {
        String message = e.getMessage();
        if (e instanceof NotLoginException) {
            return R.error(message, 401);
        }
        if (e instanceof NotRoleException || e instanceof NotPermissionException) {
            return R.error(message, 403);
        }
        return R.error(message, Code.ERROR);
    }


    // 拦截：其它所有异常
    @ExceptionHandler(Exception.class)
    public R handlerException(Exception e) {
        e.printStackTrace();
        return R.error(e.getMessage(), Code.ERROR);
    }
}
