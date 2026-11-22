package com.beat.pay.dto.record;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Data;

/** 存钱目标 */
@Data
public class GoalDTO {
    /** 目标ID */
    @NotBlank
    private String id;

    /** 版本号 */
    private Integer version;

    /** 最后修改时间 */
    private Long updatedAt;

    /** 软删除标记 */
    private Boolean deleted;

    /** 目标名称 */
    @NotBlank
    @Size(max = 100)
    private String name;

    /** 目标金额（分） */
    @NotNull
    @Min(0)
    private Long targetAmountCents;

    /** 目标日期 */
    private LocalDate targetDate;

    /** 达成激励语 */
    @Size(max = 200)
    private String rewardText;

    /** 首次达成时间 */
    private Long achievedAt;
}
