package com.learning.platform.common;

import java.util.List;

public class PageResult<T> {
    private final List<T> list;
    private final long total;
    private final int pageNum;
    private final int pageSize;
    private final int totalPages;

    public PageResult(List<T> list, long total, int pageNum, int pageSize) {
        this.list = list;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.totalPages = pageSize == 0 ? 0 : (int) Math.ceil(total * 1.0 / pageSize);
    }

    public List<T> getList() {
        return list;
    }

    public long getTotal() {
        return total;
    }

    public int getPageNum() {
        return pageNum;
    }

    public int getPageSize() {
        return pageSize;
    }

    public int getTotalPages() {
        return totalPages;
    }
}
