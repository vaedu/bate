package com.beat.pay.dto.query;

import com.beat.pay.enums.Shift;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 工时记录查询（API-DAT-02） */
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkRecordQuery extends PageQuery {
    /** 起始归属日期（含） */
    private LocalDate dateFrom;

    /** 结束归属日期（含） */
    private LocalDate dateTo;

    /** 标签（可重复，OR 关系） */
    private List<String> tag;

    /** 班次 */
    private Shift shift;

    /** 关键字：匹配备注、标签、日期 */
    private String q;

    /** 是否包含墓碑 */
    private Boolean includeDeleted = false;
}
