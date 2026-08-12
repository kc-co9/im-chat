package com.co.kc.imchat.common.exception;

import com.co.kc.imchat.common.constant.HttpErrorCode;

/**
 * 提示异常
 * <p>
 * 主要弹出toast提醒用户
 *
 * @author kc
 */
public class ToastException extends BaseException {
    public ToastException(String msg) {
        super(HttpErrorCode.OPERATE_ERROR.getCode(), msg, "Toast用户", null);
    }
}
