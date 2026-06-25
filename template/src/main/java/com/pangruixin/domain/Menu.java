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
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Menu {
    @TableId(type = IdType.AUTO)
    @JsonSerialize(using = ToStringSerializer.class)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
     protected Long id ;

//    父菜单1节点
     protected  Long parentId;

//     菜单类型：M:目录/C:菜单/F:按钮
     protected  char menuType;

//     排序号
     protected  int orderNum;

     protected String path;

     @NotBlank(message = "菜单名不能为空")
     protected String name;

     protected String component;

     @NotNull(message = "菜单隐藏状态必选")
     protected int visible;//菜单隐藏状态 0显示 1隐藏
     @NotNull(message = "菜单启用状态必选")
     protected int status;//菜单状态 0正常 1停用

     protected String perms;//

     protected String icon; //

     @TableField(exist = false)
     protected List<Menu> children;

     @TableField(fill = FieldFill.INSERT)
     protected Long createBy;

     @TableField(fill = FieldFill.INSERT)
     protected LocalDateTime createTime;

     @TableField(fill = FieldFill.UPDATE)
     protected Long updateBy;

     @TableField(fill = FieldFill.INSERT_UPDATE)
     protected LocalDateTime updateTime;

     @TableLogic
     protected int deleted;

     protected String remark;

}
