package com.beat.pay.dto.record;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Data;

/** 薪资台账 */
@Data
public class PayrollDTO {
    /** 台账ID */
    @NotBlank
    private String id;

    /** 版本号 */
    private Integer version;

    /** 最后修改时间 */
    private Long updatedAt;

    /** 软删除标记 */
    private Boolean deleted;

    /** 工资月份 YYYY-MM */
    @NotBlank
    @Size(min = 7, max = 7)
    private String payMonth;

    /** 发薪日 */
    private LocalDate payDate;

    /** 应发（分） */
    @NotNull
    private Long grossCents;

    /** 实发（分） */
    @NotNull
    private Long netCents;

    /** 是否已到账 */
    private Boolean received;

    /** 备注 */
    @Size(max = 200)
    private String note;
}
