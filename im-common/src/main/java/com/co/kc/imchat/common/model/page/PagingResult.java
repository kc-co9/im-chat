package com.co.kc.imchat.common.model.page;

import com.co.kc.imchat.common.utils.AssertUtils;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.function.Function;

/**
 * 不依赖持久化框架的分页结果。
 *
 * @param paging 当前分页边界
 * @param records 当前页记录
 * @param total   满足查询条件的总记录数
 * @param <T>     记录类型
 */
public record PagingResult<T>(Paging paging, List<T> records, Long total) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public PagingResult {
        AssertUtils.argNotNull("paging must not be null", paging);
        AssertUtils.argNotNull("records must not be null", records);
        AssertUtils.argNotNull("total must not be null", total);
        AssertUtils.argTrue("total must not be negative", total >= 0);
        records = List.copyOf(records);
    }

    /**
     * 创建分页结果 Builder。
     *
     * @param <T> 记录类型
     * @return Builder
     */
    public static <T> Builder<T> newBuilder() {
        return new Builder<>();
    }

    /**
     * 创建默认第一页的空分页结果。
     *
     * @param <T> 记录类型
     * @return 空分页结果
     */
    public static <T> PagingResult<T> empty() {
        return PagingResult.<T>newBuilder()
                .paging(new Paging(1, 20))
                .records(List.of())
                .total(0L)
                .build();
    }

    /**
     * 创建指定分页边界的空分页结果。
     *
     * @param paging 分页边界
     * @param <T> 记录类型
     * @return 空分页结果
     */
    public static <T> PagingResult<T> empty(Paging paging) {
        return PagingResult.<T>newBuilder()
                .paging(paging)
                .records(List.of())
                .total(0L)
                .build();
    }

    /**
     * 计算总页数。
     *
     * @return 总页数，无记录时返回 0
     */
    public long totalPages() {
        if (total == 0) {
            return 0;
        }
        long pages = total / paging.pageSize();
        return total % paging.pageSize() == 0 ? pages : pages + 1;
    }

    public boolean hasPrevious() {
        return paging.pageNo() > 1;
    }

    public boolean hasNext() {
        return paging.pageNo() < totalPages();
    }

    /**
     * 映射当前页记录并保留分页事实。
     *
     * @param mapper 记录映射函数
     * @param <R> 目标记录类型
     * @return 映射后的分页结果
     */
    public <R> PagingResult<R> map(Function<? super T, R> mapper) {
        AssertUtils.argNotNull("mapper must not be null", mapper);
        return PagingResult.<R>newBuilder()
                .paging(paging)
                .records(records.stream().map(mapper).toList())
                .total(total)
                .build();
    }

    /** 分页结果 Builder。 */
    public static final class Builder<T> {
        private Paging paging;
        private List<T> records;
        private Long total;

        private Builder() {
        }

        public Builder<T> paging(Paging paging) {
            this.paging = paging;
            return this;
        }

        public Builder<T> records(List<T> records) {
            this.records = records;
            return this;
        }

        public Builder<T> total(Long total) {
            this.total = total;
            return this;
        }

        public PagingResult<T> build() {
            AssertUtils.argNotNull("total must not be null", total);
            return new PagingResult<>(paging, records, total);
        }
    }
}
