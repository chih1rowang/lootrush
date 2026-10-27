-- LootRush 数据库结构（随循环逐步追加）
-- 当前：循环1 用户表

CREATE TABLE IF NOT EXISTS `user` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username`      VARCHAR(50)  NOT NULL COMMENT '用户名',
  `password_hash` VARCHAR(100) NOT NULL COMMENT '密码的BCrypt加盐哈希（绝不存明文）',
  `diamond`       INT          NOT NULL DEFAULT 1000 COMMENT '钻石余额（注册赠送1000）',
  `real_name`     VARCHAR(300) NULL COMMENT '实名姓名（AES-GCM密文，enc:v1:前缀，循环9C）',
  `id_card`       VARCHAR(300) NULL COMMENT '身份证号（AES-GCM密文，循环9C）',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';
-- 已有环境的升级语句（MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，新环境直接用上面的建表语句）：
-- ALTER TABLE user ADD COLUMN real_name VARCHAR(300) NULL, ADD COLUMN id_card VARCHAR(300) NULL;

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

-- 循环5：背包 + 发货记录
CREATE TABLE IF NOT EXISTS `bag` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT   NOT NULL,
  `item_id`     BIGINT   NOT NULL,
  `count`       INT      NOT NULL DEFAULT 0 COMMENT '数量堆叠',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_item` (`user_id`, `item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='背包';

CREATE TABLE IF NOT EXISTS `deliver_flow` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `order_no`    VARCHAR(40) NOT NULL COMMENT '一单只发一次的幂等锚点',
  `user_id`     BIGINT      NOT NULL,
  `item_id`     BIGINT      NOT NULL,
  `qty`         INT         NOT NULL,
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='发货记录(只增不改)';

-- 循环6：库存流水
CREATE TABLE IF NOT EXISTS `stock_flow` (
  `id`           BIGINT      NOT NULL AUTO_INCREMENT,
  `goods_id`     BIGINT      NOT NULL,
  `change_count` INT         NOT NULL COMMENT '带符号变动：售出-1/回补+1',
  `reason`       VARCHAR(20) NOT NULL COMMENT 'SALE/CLOSE_BACK/COMPENSATE/RECONCILE',
  `order_no`     VARCHAR(40) NOT NULL,
  `create_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_goods` (`goods_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存流水(只增不改):对账库存核对数据源';

-- 循环7：对账差异登记与修复闭环
CREATE TABLE IF NOT EXISTS `recon_diff` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `recon_date`  DATE         NOT NULL,
  `diff_type`   VARCHAR(30)  NOT NULL COMMENT 'STOCK_MISMATCH/DELIVER_GAP/DELIVER_WITHOUT_PAY',
  `order_no`    VARCHAR(60)  NOT NULL COMMENT '差异定位:订单号或 GOODS:{商品id}',
  `detail`      VARCHAR(500) NOT NULL,
  `status`      VARCHAR(10)  NOT NULL DEFAULT 'NEW' COMMENT 'NEW待处理/FIXED已修复',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_type_ref` (`diff_type`, `order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对账差异登记(同一差异只登记一次)';

-- 循环8：抽卡三件套
CREATE TABLE IF NOT EXISTS `gacha_pool` (
  `id`      BIGINT  NOT NULL AUTO_INCREMENT,
  `item_id` BIGINT  NOT NULL,
  `rarity`  TINYINT NOT NULL,
  `weight`  INT     NOT NULL COMMENT '概率=weight/Σweight,对外公示',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_item` (`item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='卡池';

CREATE TABLE IF NOT EXISTS `gacha_record` (
  `id`               BIGINT      NOT NULL AUTO_INCREMENT,
  `user_id`          BIGINT      NOT NULL,
  `item_id`          BIGINT      NOT NULL,
  `rarity`           TINYINT     NOT NULL,
  `pity_count_after` INT         NOT NULL COMMENT '本抽后的保底计数(0=刚出金清零)',
  `is_pity_hit`      TINYINT     NOT NULL DEFAULT 0 COMMENT '是否硬保底强制出金',
  `batch_no`         VARCHAR(40) NOT NULL,
  `create_time`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='抽卡流水(只增不改):概率审计原始账本';

CREATE TABLE IF NOT EXISTS `user_pity` (
  `user_id`    BIGINT   NOT NULL,
  `pity_count` INT      NOT NULL DEFAULT 0,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='保底计数:丢失容忍度=0,落MySQL不落Redis(选型故事)';

-- 卡池种子（幂等：按道具名判重）
INSERT INTO item(name, rarity, source_type, description) SELECT '星尘碎片',1,'GACHA','最常见的星尘' WHERE NOT EXISTS (SELECT 1 FROM item WHERE name='星尘碎片');
INSERT INTO item(name, rarity, source_type, description) SELECT '辉光结晶',2,'GACHA','罕见的辉光' WHERE NOT EXISTS (SELECT 1 FROM item WHERE name='辉光结晶');
INSERT INTO item(name, rarity, source_type, description) SELECT '逐星者之刃',3,'GACHA','传说中的逐星者遗物' WHERE NOT EXISTS (SELECT 1 FROM item WHERE name='逐星者之刃');
INSERT INTO gacha_pool(item_id, rarity, weight) SELECT i.id, 1, 890 FROM item i WHERE i.name='星尘碎片' AND NOT EXISTS (SELECT 1 FROM gacha_pool p WHERE p.item_id=i.id);
INSERT INTO gacha_pool(item_id, rarity, weight) SELECT i.id, 2, 100 FROM item i WHERE i.name='辉光结晶' AND NOT EXISTS (SELECT 1 FROM gacha_pool p WHERE p.item_id=i.id);
INSERT INTO gacha_pool(item_id, rarity, weight) SELECT i.id, 3, 10 FROM item i WHERE i.name='逐星者之刃' AND NOT EXISTS (SELECT 1 FROM gacha_pool p WHERE p.item_id=i.id);

-- 循环9D：金装每周轮换 + 商城两级稀有度
CREATE TABLE IF NOT EXISTS `gacha_gold_item` (
  `id`         BIGINT NOT NULL AUTO_INCREMENT,
  `item_id`    BIGINT NOT NULL,
  `slot_order` INT    NOT NULL COMMENT '轮换队列顺序（5个一组，按周轮换）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_item` (`item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='金装轮换队列';

CREATE TABLE IF NOT EXISTS `gacha_rotation_state` (
  `id`           TINYINT  NOT NULL,
  `anchor_start` DATETIME NOT NULL COMMENT '轮换纪元起点（当前期=anchor+N*7天）',
  `period_days`  INT      NOT NULL DEFAULT 7,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='轮换状态(单行)';

-- 金装从基础卡池移出（金色只能抽卡获得，且按周轮换）：
-- DELETE FROM gacha_pool WHERE item_id IN (SELECT item_id FROM gacha_gold_item);
-- 商城道具种子（蓝/紫两级）：见试短剑/星纹护符/疾风之靴(蓝)、苍穹之翼碎片/龙鳞胸甲/幻月法环(紫)
