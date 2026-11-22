package com.beat.pay.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

/** 自动填充 createdAt / updatedAt（毫秒时间戳） */
@Component
public class AutoFillHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        long now = System.currentTimeMillis();
        this.strictInsertFill(metaObject, "createdAt", Long.class, now);
        this.strictInsertFill(metaObject, "updatedAt", Long.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt", Long.class, System.currentTimeMillis());
    }
}
