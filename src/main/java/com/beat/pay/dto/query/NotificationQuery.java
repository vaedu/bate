package com.beat.pay.dto.query;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 通知查询（API-DAT-07） */
@Data
@EqualsAndHashCode(callSuper = true)
public class NotificationQuery extends PageQuery {
    /** 按已读状态筛选 */
    private Boolean read;
}
