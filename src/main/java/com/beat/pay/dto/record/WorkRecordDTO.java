package com.beat.pay.dto.record;

import com.beat.pay.enums.Shift;
import com.beat.pay.enums.WorkSource;
import com.beat.pay.model.PayRule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;

/** 工时记录（含分段） */
@Data
public class WorkRecordDTO {
    /** 记录ID（客户端生成UUID） */
    @NotBlank
    @Size(max = 36)
    private String id;

    /** 服务端版本号，提交时无需填写 */
    private Integer version;

    /** 最后修改时间 */
    private Long updatedAt;

    /** 软删除标记 */
    private Boolean deleted;

    /** 归属日期 */
    @NotNull
    private LocalDate workDate;

    /** 开始时间 */
    @NotNull
    private Long startAt;

    /** 结束时间 */
    @NotNull
    private Long endAt;

    /** 总工时（毫秒） */
    @NotNull
    @Min(0)
    private Long durationMs;

    /** 总金额（分） */
    @NotNull
    @Min(0)
    private Long totalEarnCents;

    /** 班次，可空 */
    private Shift shift;

    /** 标签 */
    private List<String> tags;

    /** 备注 */
    @Size(max = 1000)
    private String note;

    /** 是否跨天 */
    private Boolean crossDay;

    /** 来源 */
    private WorkSource source;

    /** 冲突副本指向的原记录ID */
    private String conflictOf;

    /** 计薪规则快照 */
    @Valid
    private PayRule ruleSnapshot;

    /** 分段 */
    @NotNull
    @Valid
    private List<WorkSegmentDTO> segments;
}
