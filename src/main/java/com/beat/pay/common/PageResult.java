package com.beat.pay.common;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 分页结果：{ items, page, size, total, hasNext } */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    private List<T> items;
    private long page;
    private long size;
    private long total;
    private boolean hasNext;

    public static <T> PageResult<T> of(List<T> items, long page, long size, long total) {
        return new PageResult<>(items, page, size, total, page * size < total);
    }
}
