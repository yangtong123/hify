package com.hify.common.demo.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.po.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_demo_item")
public class DemoItemPo extends BaseEntity {

    private String name;

    private Integer status;
}
