package edu.upc.essi.dtim.odin.storage;

import java.util.List;

public class Page<T> {
    private final List<T> items;
    private final long total;
    private final int offset;
    private final int limit;

    public Page(List<T> items, long total, int offset, int limit) {
        this.items = items;
        this.total = total;
        this.offset = offset;
        this.limit = limit;
    }

    public List<T> getItems() { return items; }
    public long getTotal() { return total; }
    public int getOffset() { return offset; }
    public int getLimit() { return limit; }
}
