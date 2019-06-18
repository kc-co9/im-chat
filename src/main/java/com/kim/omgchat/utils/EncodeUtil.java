package com.kim.omgchat.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Objects;

public class EncodeUtil {
    /**
     * 利用MD5进行加密
     *
     * @param str 待加密的字符串
     * @return 加密后的字符串
     */
    public static String encoderByMd5(String str) {
        //确定计算方法
        MessageDigest md5 = null;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        Base64.Encoder encoder = Base64.getEncoder();
//        JDK 10 sun.misc套件提供的base64编解码方式已经被删除
//        BASE64Encoder base64en = new BASE64Encoder();
        //加密后的字符串
        return encoder.encodeToString(Objects.requireNonNull(md5).digest(str.getBytes(StandardCharsets.UTF_8)));
     }
}
