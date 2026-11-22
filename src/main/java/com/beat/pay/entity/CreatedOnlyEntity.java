package com.beat.pay.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

/** 只有创建时间的表（同步日志、幂等记录） */
@Data
public abstract class CreatedOnlyEntity {

    @TableField(fill = FieldFill.INSERT)
    private Long createdAt;
}
