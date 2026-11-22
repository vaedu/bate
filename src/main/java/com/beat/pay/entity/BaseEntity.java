package com.beat.pay.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

/** 通用字段：服务端创建/更新时间（毫秒，UTC），由 AutoFillHandler 自动填充 */
@Data
public abstract class BaseEntity {

    @TableField(fill = FieldFill.INSERT)
    private Long createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedAt;
}
