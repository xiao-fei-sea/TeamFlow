package com.teamflow.common.result;

import java.util.List;

/**
 * 通用分页响应（接口设计文档 1.2）：{@code { list, total, page, size, pages }}。
 */
public record PageVO<T>(List<T> list, long total, long page, long size, long pages) {

    /** page 默认 1 且 ≥1；size 默认 10，范围 1–100，超出按 100（PRD 6.2） */
    public static int[] sanitize(Integer page, Integer size) {
        int p = (page == null || page < 1) ? 1 : page;
        int s = (size == null || size < 1) ? 10 : Math.min(size, 100);
        return new int[]{p, s};
    }

    public static <T> PageVO<T> of(long page, long size, long total, List<T> list) {
        long pages = size <= 0 ? 0 : (total + size - 1) / size;
        return new PageVO<>(list, total, page, size, pages);
    }
}
