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

-- 循环3：订单表。限购铁闸：UNIQUE(user_id, goods_id, active_flag)——
-- 订单活跃时 active_flag=1（同用户同商品只能有一张活跃单），关单时置 NULL（放行再抢）。
-- MySQL 唯一索引不约束 NULL，一条索引同时实现"活跃期唯一+关闭后放行"。
CREATE TABLE IF NOT EXISTS `order_info` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT,
  `order_no`      VARCHAR(40) NOT NULL COMMENT '订单号(全局唯一)',
  `type`          VARCHAR(10) NOT NULL DEFAULT 'PURCHASE' COMMENT 'PURCHASE购买/RECHARGE充值',
  `user_id`       BIGINT      NOT NULL,
  `goods_id`      BIGINT      NOT NULL,
  `price_diamond` INT         NOT NULL,
  `status`        TINYINT     NOT NULL DEFAULT 0 COMMENT '0待支付/1已支付/2已发货/3已关闭',
  `active_flag`   BIGINT      NULL COMMENT '活跃订单=order_no;关单置NULL',
  `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  UNIQUE KEY `uk_user_goods_active` (`user_id`, `goods_id`, `active_flag`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';

-- 循环4：支付通知日志（只增不改）+ 钱包流水（只增不改）
CREATE TABLE IF NOT EXISTS `pay_notify_log` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `notify_no`   VARCHAR(64) NOT NULL COMMENT '支付公司生成的通知唯一号',
  `order_no`    VARCHAR(40) NOT NULL,
  `amount`      INT         NOT NULL,
  `result`      VARCHAR(30) NOT NULL COMMENT 'PENDING/PROCESSED/DUP_NOTIFY/DUP_STATE/BAD_SIGN/AMOUNT_MISMATCH/NO_ORDER/NO_BALANCE',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notify_no` (`notify_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付通知日志:重发N次只处理1次的证据';

CREATE TABLE IF NOT EXISTS `wallet_flow` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT,
  `user_id`       BIGINT      NOT NULL,
  `change_type`   VARCHAR(20) NOT NULL COMMENT 'RECHARGE/PURCHASE/GACHA',
  `amount`        INT         NOT NULL COMMENT '带符号变动量',
  `balance_after` INT         NOT NULL COMMENT '变动后余额',
  `ref_order_no`  VARCHAR(40) NOT NULL,
  `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='钱包流水:对账数据源';
