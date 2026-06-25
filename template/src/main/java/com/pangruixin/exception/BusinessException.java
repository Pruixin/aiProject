package com.pangruixin.exception;

import lombok.Data;
/**
 * 业务异常类
 */
@Data
public class BusinessException extends RuntimeException{

    private Integer code;

    public BusinessException(Integer code,String message){
        super(message);
        this.code = code;
    }
}
