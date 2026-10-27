package com.lootrush.common;

import java.util.regex.Pattern;

/**
 * 身份证号校验：18位 + GB 11643-1999 校验位算法。
 * 游戏实名认证（防沉迷）的合规前提之一是号码本身真实有效——
 * 只做格式正则不校验校验位，会被一句"你这是假证号也收？"问倒。
 */
public final class IdCardUtil {

    private static final Pattern FORMAT = Pattern.compile("^\\d{17}[\\dXx]$");
    private static final int[] WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    private static final char[] CHECK_CODES = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

    private IdCardUtil() {
    }

    public static boolean isValid(String idCard) {
        if (idCard == null || !FORMAT.matcher(idCard).matches()) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += (idCard.charAt(i) - '0') * WEIGHTS[i];
        }
        char expected = CHECK_CODES[sum % 11];
        return Character.toUpperCase(idCard.charAt(17)) == expected;
    }
}
