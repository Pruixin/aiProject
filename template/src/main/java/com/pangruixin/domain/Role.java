package com.pangruixin.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class Role {

    @TableId(type = IdType.AUTO)
    @JsonSerialize(using = ToStringSerializer.class)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    protected Long id;

    @NotBlank(message = "角色名称不能为空")
    protected String name;

    @NotBlank(message = "角色键不能为空")
    protected String roleKey;

    @NotNull(message = "角色状态必选")
    protected int status;

    @TableField(fill = FieldFill.INSERT)
    protected Long createBy;

    @TableField(fill = FieldFill.INSERT)
    protected LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    protected Long updateBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    protected LocalDateTime updateTime;

    @TableLogic
    protected int deleted;

    protected String remark;
}
