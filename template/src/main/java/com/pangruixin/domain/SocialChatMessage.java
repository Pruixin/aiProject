package com.pangruixin.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("social_chat_message")
public class SocialChatMessage {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Long senderId;

    private Long receiverId;

    private String content;

    private Integer isRead;

    private LocalDateTime createTime;
}
