package com.beat.pay.dto.record;

import com.beat.pay.enums.Shift;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Data;

/** 排班 */
@Data
public class ScheduleDTO {
    /** 同步实体ID=日期 YYYY-MM-DD */
    private String id;

    /** 版本号 */
    private Integer version;

    /** 最后修改时间 */
    private Long updatedAt;

    /** 软删除标记 */
    private Boolean deleted;

    /** 日期 */
    @NotNull
    private LocalDate date;

    /** 班次 */
    @NotNull
    private Shift shift;
}
