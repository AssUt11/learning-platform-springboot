package com.learning.platform.dto;

import java.util.List;

public class ReorderRequest {
    private List<Long> ids;

    public List<Long> getIds() { return ids; }
    public void setIds(List<Long> ids) { this.ids = ids; }
}
