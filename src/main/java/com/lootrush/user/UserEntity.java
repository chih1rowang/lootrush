package com.lootrush.user;

import lombok.Data;

import java.time.LocalDateTime;

/** 用户表对应的一行数据 */
@Data
public class UserEntity {
    private Long id;
    private String username;
    private String passwordHash;
    private Integer diamond;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
