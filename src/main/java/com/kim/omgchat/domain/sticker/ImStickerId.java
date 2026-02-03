package com.kim.omgchat.domain.sticker;

import lombok.Getter;

@Getter
public class ImStickerId {

    private final String value;

    public ImStickerId(String value) {
        if (value != null && value.trim().isEmpty()) {
            throw new IllegalArgumentException("StickerId cannot be empty");
        }
        this.value = value;
    }

    public static ImStickerId of(String value) {
        return new ImStickerId(value);
    }

    public static ImStickerId empty() {
        return new ImStickerId(null);
    }

    public boolean isEmpty() {
        return value == null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ImStickerId that = (ImStickerId) o;
        if (value != null) return value.equals(that.value);
        return that.value == null;
    }

    @Override
    public int hashCode() {
        return value != null ? value.hashCode() : 0;
    }

    @Override
    public String toString() {
        return value != null ? value : "";
    }
}
