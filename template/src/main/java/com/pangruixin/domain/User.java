package com.pangruixin.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.NotBlank;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class User implements Serializable {
    @TableId(type = IdType.AUTO)
    @JsonSerialize(using = ToStringSerializer.class)
    protected Long id;

    @NotBlank(message = "手机号不能为空", groups = {First.class, Second.class})
    @Pattern(regexp = "^(13[0-9]|14[01456879]|15[0-35-9]|16[2567]|17[0-8]|18[0-9]|19[0-35-9])\\d{8}$", message = "手机号格式不正确", groups = {First.class, Second.class})
    protected String phone;

    @NotNull(message = "密码不能为空", groups = {First.class, Second.class})
    @Pattern(regexp = "^[a-zA-Z]\\w{5,17}$", message = "密码以字母开头,长度在6~18之间,只能包含字母、数字和下划线", groups = {First.class, Second.class})
    protected String password;

    protected String avatar;

    @NotNull(message = "用户状态必选", groups = {Second.class})
    protected Integer enable;

    @NotNull(message = "性别必选", groups = {Second.class})
    protected Integer sex;

    protected String nickName;

    @NotBlank(message = "所属地区不能为空", groups = {Second.class})
    protected String location;

    @TableField(exist = false)
    @NotBlank(message = "搭子目标不能为空", groups = {Second.class})
    protected String socialGoal;

    protected Integer matchTodayCount;

    protected LocalDate matchDate;

    @TableField(fill = FieldFill.INSERT)
    protected Long createBy;

    @TableField(fill = FieldFill.INSERT)
    protected Long updateBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    protected LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    protected LocalDateTime updateTime;

    public interface First {

    }

    public interface Second {

    }
}
