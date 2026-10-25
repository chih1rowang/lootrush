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

-- 循环2：道具字典 + 贩售商品
CREATE TABLE IF NOT EXISTS `item` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '道具ID',
  `name`        VARCHAR(50)  NOT NULL COMMENT '道具名',
  `rarity`      TINYINT      NOT NULL DEFAULT 1 COMMENT '稀有度:1蓝/2紫/3金',
  `source_type` VARCHAR(20)  NOT NULL DEFAULT 'SHOP' COMMENT '来源:SHOP贩售/GACHA抽卡/BOTH',
  `description` VARCHAR(200) NULL COMMENT '描述',
  `image_url`   VARCHAR(300) NULL COMMENT '图片地址',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='道具字典';

CREATE TABLE IF NOT EXISTS `goods` (
  `id`              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '商品ID',
  `item_id`         BIGINT      NOT NULL COMMENT '关联道具',
  `price_diamond`   INT         NOT NULL COMMENT '售价(钻石)',
  `original_price`  INT         NOT NULL COMMENT '划线原价(游戏味)',
  `stock_total`     INT         NOT NULL COMMENT '总库存',
  `sale_start_time` DATETIME    NOT NULL COMMENT '开售时间',
  `sale_end_time`   DATETIME    NOT NULL COMMENT '结束时间',
  `create_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_sale_time` (`sale_start_time`, `sale_end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='贩售商品(四态由时间+库存推导,不落库)';
