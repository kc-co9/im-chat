package com.kim.omgchat.model.io;

import com.kim.omgchat.common.constant.ErrorCode;
import com.kim.omgchat.common.constant.ResultCode;
import com.kim.omgchat.common.exception.BaseException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.Map;

/**
 * <p>
 * TODO
 * </p>
 *
 * @author kim
 * @since 2019/6/5 16:27
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    /**
     * result code
     */
    private Integer code;
    /**
     * result additional info
     */
    private String msg;
    /**
     * result content
     */
    private T data;

    public static Result<Map<String, Object>> success() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMsg(), Collections.emptyMap());
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMsg(), data);
    }

    public static Result<Map<String, Object>> error(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMsg(), Collections.emptyMap());
    }

    public static Result<Map<String, Object>> error(ErrorCode errorCode, String msg) {
        return new Result<>(errorCode.getCode(), msg, Collections.emptyMap());
    }

    public static Result<Map<String, Object>> error(BaseException ex) {
        return new Result<>(ex.getCode(), ex.getMsg(), Collections.emptyMap());
    }
}
