package com.hify.modules.chat.infra.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_chat_session")
public class ChatSessionPo extends BaseEntity {

    @TableField("agent_id")
    private Long agentId;

    @TableField("title")
    private String title;

    @TableField("user_id")
    private String userId;

    @TableField("status")
    private String status;
}
