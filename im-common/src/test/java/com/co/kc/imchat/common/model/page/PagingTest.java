package com.co.kc.imchat.common.model.page;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PagingTest {

    @Test
    void exposesValidatedPageBounds() {
        Paging paging = new Paging(2, 20);

        assertThat(paging.pageNo()).isEqualTo(2);
        assertThat(paging.pageSize()).isEqualTo(20);
    }

    @Test
    void rejectsNonPositiveBounds() {
        assertThatThrownBy(() -> new Paging(0, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Paging(1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
