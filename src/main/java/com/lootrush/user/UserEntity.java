package com.lootrush.user;

import com.lootrush.config.EncryptedField;
import lombok.Data;

import java.time.LocalDateTime;

/** 用户表对应的一行数据 */
@Data
public class UserEntity {
    private Long id;
    private String username;
    private String passwordHash;
    private Integer diamond;

    /** 实名信息（游戏防沉迷合规要求）：注解标记后由 CryptoInterceptor 透明加解密，
     *  业务代码全程只接触明文，库里永远是密文 */
    @EncryptedField
    private String realName;
    @EncryptedField
    private String idCard;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
