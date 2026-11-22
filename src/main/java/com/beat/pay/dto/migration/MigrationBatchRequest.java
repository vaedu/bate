package com.beat.pay.dto.migration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/** 分批提交（API-MIG-02） */
@Data
public class MigrationBatchRequest {
    /** 批号，从1递增 */
    @NotNull
    @Min(1)
    private Integer batchNo;

    /** 本批条目，建议≤200 */
    @NotNull
    @Size(max = 500)
    @Valid
    private List<MigrationItem> items;
}
