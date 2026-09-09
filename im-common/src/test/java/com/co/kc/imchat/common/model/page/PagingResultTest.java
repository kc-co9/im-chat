package com.co.kc.imchat.common.model.page;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PagingResultTest {

    @Test
    void buildsValidatedImmutablePagingResult() {
        Paging paging = new Paging(2, 20);
        List<String> records = new ArrayList<>(List.of("first", "second"));

        PagingResult<String> result = PagingResult.<String>newBuilder()
                .paging(paging)
                .records(records)
                .total(41L)
                .build();

        records.clear();
        assertThat(result.paging()).isEqualTo(paging);
        assertThat(result.records()).containsExactly("first", "second");
        assertThat(result.total()).isEqualTo(41L);
    }

    @Test
    void createsEmptyPagingResults() {
        Paging paging = new Paging(3, 20);

        PagingResult<String> defaultEmpty = PagingResult.empty();
        PagingResult<String> requestedPageEmpty = PagingResult.empty(paging);

        assertThat(defaultEmpty.paging()).isEqualTo(new Paging(1, 20));
        assertThat(defaultEmpty.records()).isEmpty();
        assertThat(defaultEmpty.total()).isZero();
        assertThat(requestedPageEmpty.paging()).isEqualTo(paging);
        assertThat(requestedPageEmpty.records()).isEmpty();
        assertThat(requestedPageEmpty.total()).isZero();
    }

    @Test
    void rejectsIncompleteBuilder() {
        assertThatThrownBy(() -> PagingResult.newBuilder().build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PagingResult.newBuilder()
                .paging(new Paging(1, 20))
                .records(List.of())
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void providesImmutableRecordsAndNavigationFacts() {
        List<String> source = new ArrayList<>(List.of("first", "second"));
        PagingResult<String> result = new PagingResult<>(new Paging(2, 20), source, 41L);

        source.clear();

        assertThat(result.records()).containsExactly("first", "second");
        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(result.hasPrevious()).isTrue();
        assertThat(result.hasNext()).isTrue();
        assertThatThrownBy(() -> result.records().add("third"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void createsEmptyResultForRequestedPage() {
        Paging paging = new Paging(1, 20);

        PagingResult<String> result = new PagingResult<>(paging, List.of(), 0L);

        assertThat(result.paging()).isEqualTo(paging);
        assertThat(result.records()).isEmpty();
        assertThat(result.total()).isZero();
        assertThat(result.totalPages()).isZero();
        assertThat(result.hasPrevious()).isFalse();
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    void mapsRecordsWithoutChangingPageFacts() {
        PagingResult<String> source = new PagingResult<>(
                new Paging(3, 10), List.of("1", "2"), 25L);

        PagingResult<Integer> result = source.map(Integer::valueOf);

        assertThat(result.paging()).isEqualTo(source.paging());
        assertThat(result.records()).containsExactly(1, 2);
        assertThat(result.total()).isEqualTo(25);
        assertThat(result.hasPrevious()).isTrue();
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    void calculatesTotalPagesWithoutOverflow() {
        PagingResult<String> result = new PagingResult<>(
                new Paging(1, 20), List.of(), Long.MAX_VALUE);

        assertThat(result.totalPages()).isEqualTo(Long.MAX_VALUE / 20 + 1);
    }

    @Test
    void rejectsInvalidPageFacts() {
        assertThatThrownBy(() -> new PagingResult<>(null, List.of(), 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PagingResult<>(new Paging(1, 20), null, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PagingResult<>(new Paging(1, 20), List.of(), -1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PagingResult<>(
                new Paging(1, 20), List.of("value"), 1L).map(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
