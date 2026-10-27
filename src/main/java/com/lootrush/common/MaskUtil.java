package com.lootrush.common;

/** 脱敏工具：敏感字段出参遮罩（库里密文、接口半遮、日志全无） */
public final class MaskUtil {

    private MaskUtil() {
    }

    /** 身份证：保留前3后4，中间6星。例：310110********1234 */
    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 8) {
            return idCard;
        }
        return idCard.substring(0, 3) + "******" + idCard.substring(idCard.length() - 4);
    }

    /** 姓名：保留首字，其余以星代。例：张*三 */
    public static String maskName(String name) {
        if (name == null || name.length() <= 1) {
            return name;
        }
        return name.charAt(0) + "*".repeat(name.length() - 1);
    }
}
