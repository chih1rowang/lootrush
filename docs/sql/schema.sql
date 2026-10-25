-- LootRush 数据库结构（随循环逐步追加）
-- 当前：循环1 用户表

CREATE TABLE IF NOT EXISTS `user` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username`      VARCHAR(50)  NOT NULL COMMENT '用户名',
  `password_hash` VARCHAR(100) NOT NULL COMMENT '密码的BCrypt加盐哈希（绝不存明文）',
  `diamond`       INT          NOT NULL DEFAULT 1000 COMMENT '钻石余额（注册赠送1000）',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';
