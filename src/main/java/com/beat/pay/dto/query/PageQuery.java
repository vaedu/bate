package com.beat.pay.dto.query;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/** 分页基类 */
@Data
public class PageQuery {
    /** 页码，从1开始 */
    @Min(1)
    private Integer page = 1;

    /** 每页条数，最大100 */
    @Min(1)
    @Max(100)
    private Integer size = 20;

    /** 排序：字段,方向（asc|desc），仅允许各接口声明的字段 */
    private String sort;
}
