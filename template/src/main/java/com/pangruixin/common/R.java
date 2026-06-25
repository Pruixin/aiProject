package com.pangruixin.common;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 响应结果返回类
 */
@Data
public class R<T> {
    private Integer code; //编码：1成功，0和其它数字为失败
    private String msg; //错误信息
    private T data; //数据
    private Map<String,Object> map = new HashMap<>(); //动态数据

    public static <T> R<T> success(Integer code,T data) {
        R<T> r = new R<T>();
        r.msg = "操作成功";
        r.data = data;
        r.code = Code.SUCCESS;
        return r;
    }
    public static <T> R<T> success(String msg) {
        R<T> r = new R<T>();
        r.msg = msg;
        r.data = null;
        r.code = Code.SUCCESS;
        return r;
    }
    public static <T> R<T> success(T object) {
        R<T> r = new R<T>();
        r.msg = "操作成功";
        r.data = object;
        r.code = Code.SUCCESS;
        return r;
    }

    public static <T> R<T> success(String message,T object){
        R result = new R();
        result.msg = message;
        result.data=object;
        result.code = Code.SUCCESS;
        return result;
    }

    public static <T> R<T> error(String msg) {
        R r = new R();
        r.msg = msg;
        r.code = Code.ERROR;
        return r;
    }
    public static <T> R<T> error(String msg,Integer code) {
        R r = new R();
        r.msg = msg;
        r.code = code;
        return r;
    }

    public R<T> add(String key, Object value) {
        this.map.put(key, value);
        return this;
    }

}
