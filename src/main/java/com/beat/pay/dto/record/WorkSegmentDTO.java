package com.beat.pay.dto.record;

import com.beat.pay.model.PricingPart;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/** 工时分段 */
@Data
public class WorkSegmentDTO {
    /** 分段ID（UUID） */
    @NotBlank
    @Size(max = 36)
    private String id;

    /** 分段序号，从1开始 */
    @NotNull
    @Min(1)
    private Integer segmentNo;

    /** 开始时间（毫秒） */
    @NotNull
    private Long startAt;

    /** 结束时间（毫秒） */
    @NotNull
    private Long endAt;

    /** 时长（毫秒） */
    @NotNull
    @Min(0)
    private Long durationMs;

    /** 分段金额（分） */
    @NotNull
    @Min(0)
    private Long earnCents;

    /** 计价片段 */
    @Valid
    private List<PricingPart> parts;
}
