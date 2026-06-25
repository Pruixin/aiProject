package com.pangruixin.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("social_friend")
public class SocialFriend {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long friendUserId;

    private Integer status;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime handleTime;

    @TableLogic
    protected int deleted;
}
