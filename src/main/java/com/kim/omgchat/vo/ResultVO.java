package com.kim.omgchat.vo;

import lombok.Data;

/**
 * <p>
 * TODO
 * </p>
 *
 * @author kim
 * @since 2019/6/5 16:27
 */
@Data
public class ResultVO<T> {
    private Integer code;
    private String msg;
    private T data;

    public static ResultVO<Boolean> success() {
        return ResultVO.success(true);
    }

    public static <R> ResultVO<R> success(R data) {
        ResultVO<R> resultVO = new ResultVO<>();
        resultVO.setCode(200);
        resultVO.setMsg("success");
        resultVO.setData(data);
        return resultVO;
    }

    public static ResultVO<Boolean> failure(String msg) {
        return ResultVO.failure(msg, 10001, false);
    }

    public static <R> ResultVO<R> failure(String msg, Integer code, R data) {
        ResultVO<R> resultVO = new ResultVO<>();
        resultVO.setCode(code);
        resultVO.setMsg(msg);
        resultVO.setData(data);
        return resultVO;
    }
}
