package com.beat.pay.dto.migration;

import com.beat.pay.model.EntityCounts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 完成迁移确认（API-MIG-04） */
@Data
public class MigrationCommitRequest {
    /** 预期数量 */
    @NotNull
    @Valid
    private EntityCounts expectedCounts;

    /** 关键字段校验值 */
    private Totals totals;
}
