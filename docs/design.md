# NightMarket 系统设计 v1.0

> 依据《PRD v2.0》。本文档定义：进程与模块、数据库 13 表、Redis 键、消息主题、
> 接口清单 25 个、鉴权、订单状态机、接口文档方案、"不拆微服务"的取舍论证。

## 一、架构总览（两个进程）

```
┌─ 替身网页(玩家) ─┐   ┌─ 管理后台 ─┐
│  7 张页面        │   │  1 页式     │
└───────┬─────────┘   └─────┬──────┘
        ▼                   ▼
┌─────────────────────────────────────┐     ┌──────────────────────┐
│  nightmarket-backend :8080          │     │  mock-payment :18090 │
│  ┌ user ─ goods ─ trade ─ gacha ─┐  │回调 │  假支付公司(独立进程) │
│  │ rank ─ reconciliation ─ admin │◄┼─────┤  · 支付确认页         │
│  └───────────┬───────────────────┘  │验签 │  · 三作恶开关        │
└──────┬───────┼──────────┬───────────┘     │    (正常/延迟/重发)  │
       ▼       ▼          ▼                 └──────────────────────┘
    MySQL   Redis      RocketMQ        （充值与购买都走它回调，
   (3308)  (6379)  (namesrv 9876)       幂等/延迟/重复故事全覆盖）
       └观测：日志/Zipkin(9411)/Grafana(3000)┘
```

**微服务取舍（主动决策）**：mock-payment 是"外部公司"的替身，独立天经地义；
主系统是模块化单体。曾评估把支付回调再拆独立网关服务，结论是不拆——单机压测场景下
拆分不带来收益，只增加部署复杂度；回调入口以"独立验签＋独立限流＋状态机守卫"在单体内
实现隔离。面试答法："我基于场景评估后选择不拆，模块边界=未来服务边界。"

## 二、模块划分（包结构）

```
com.nightmarket
├── config/            # Zipkin、MyBatis、Redis、鉴权拦截器等配置
├── common/            # 统一响应体、业务异常、枚举、工具
├── user/              # 注册登录、JWT、钱包（钻石余额与流水）
├── goods/             # 道具字典、商品上架、四态货架查询、缓存
├── trade/             # 抢购、订单、支付回调、异步发货、关单回补
│   └── callback/      # 回调入口（独立验签+幂等首道，信任级别最低的入口）
├── gacha/             # 卡池、概率、保底计数、抽卡流水
├── rank/              # 消费榜 ZSET、造数脚本入口
├── reconciliation/    # 对账定时任务、差异修复
└── admin/             # 管理端聚合接口（X-Admin-Token 鉴权）
```

## 三、数据库设计（库 nightmarket，共 13 张）

通用规范：id BIGINT 自增主键；业务表带 create_time/update_time；
**流水表只增不改**（无 update_time）——审计铁律。

### 账号与钱包
| 表 | 关键列 | 说明 / 故事挂钩 |
|---|---|---|
| user | username UNIQUE, password_hash(BCrypt), diamond | 钻石余额；扣款用条件更新 `diamond>=?` 防负数 |
| wallet_flow | user_id, change_type(RECHARGE/PURCHASE/GACHA), amount(±), balance_after, ref_order_no | 只增不改；充值/消费/抽卡全记账；排行榜积分与对账的事实来源 |

### 道具与贩售
| 表 | 关键列 | 说明 |
|---|---|---|
| item | name, rarity(1蓝/2紫/3金), source_type(GACHA/SHOP), description, image_url | 道具字典：抽卡的卡和贩售的商品共用一套道具体系 |
| goods | item_id, price_diamond, original_price(划线价), stock_total, sale_start_time, sale_end_time | 商品=道具的一次贩售配置；**四态不落库，由当前时间+实时库存推导**（即将开售/抢购中/已售罄/已结束） |
| stock_flow | goods_id, change(±1), reason(SALE/CLOSE_BACK/RECONCILE), order_no | 库存每一增减都留痕；对账"库存核对"的数据源 |

### 订单与支付
| 表 | 关键列 | 说明 / 故事挂钩 |
|---|---|---|
| order_info | order_no UNIQUE, type(PURCHASE/RECHARGE), user_id, goods_id, price_diamond, status, active_flag | **限购铁闸**：`UNIQUE(user_id, goods_id, active_flag)`，订单活跃时 active_flag=order_no，关闭/完成后置 **NULL**——MySQL 唯一索引不约束 NULL，实现"同一用户同一商品同时只能有一张有效订单、关单后可再抢"。两道防线：Redis SETNX 判重(快) + 此唯一索引(铁) |
| pay_notify_log | notify_no UNIQUE, order_no, handle_result(首次处理/重复忽略) | 支付公司每次通知都记账；**"重发 3 次只处理 1 次"的证据表** |
| deliver_flow | order_no, user_id, item_id, qty | 异步发货记录；对账"付了没发/发了没付"的数据源 |

### 背包与抽卡
| 表 | 关键列 | 说明 |
|---|---|---|
| bag | user_id, item_id, count, UNIQUE(user_id,item_id) | 背包堆叠；UPSERT 增减数量 |
| gacha_pool | item_id, rarity, weight | 权重表；概率公示=weight/Σweight，页面实时算 |
| gacha_record | user_id, item_id, pity_count_after, is_pity_hit, batch_no | 只增不改；概率审计的原始账本 |
| user_pity | user_id PK, pity_count | **保底计数——落 MySQL 不落 Redis**（丢失容忍度=0，选型故事） |

### 对账
| 表 | 关键列 | 说明 |
|---|---|---|
| recon_diff | recon_date, diff_type, order_no, detail, status(NEW/FIXED) | 差异登记与修复闭环 |

## 四、Redis 键设计

| 键 | 类型 | 用途 |
|---|---|---|
| stock:{goodsId} | STRING(int) | 库存预热；Lua 原子扣减/回补 |
| buyflag:{goodsId}:{userId} | STRING | SETNX 抢购判重第一道（快），TTL=活动期 |
| goods:detail:{id} / goods:list | STRING(json) | 商品缓存（延迟双删；空值短 TTL 防穿透；热点永不过期） |
| rank:main | ZSET | 消费钻石榜（member=userId, score=累计消费） |
| lock:recon:{date} | STRING | 对账分布式锁（双实例演示用） |

## 五、消息主题（RocketMQ，引入前先验 Boot 4 兼容——既定纪律）

| Topic | 触发 | 消费者做什么 |
|---|---|---|
| ORDER_CREATED | 抢购成功（Redis 扣减+判重通过） | 建订单落库（削峰：洪峰写入变匀速） |
| DELIVER_GOODS | 支付回调处理成功 | 发货：背包 UPSERT＋deliver_flow＋订单→已发货（不丢不重+幂等） |
| ORDER_CLOSE_DELAY | 建订单时发 15 分钟延迟消息 | 关单：状态守卫校验→订单关闭＋Redis 库存回补＋stock_flow 记账 |

## 六、支付语义（统一链路的关键设计）

**充值与购买都走假支付公司回调**：充值单回调→加钻石；购买单回调→扣钻石（余额校验）
＋订单→已支付＋发 DELIVER_GOODS。支付页 15 分钟不确认=超时关单。
好处：幂等/重复/延迟的故事在两类订单上全覆盖，回调链路只有一条。

回调鉴权：HMAC-SHA256 签名（orderNo+amount+约定密钥），防伪造回调——
"为什么信这个通知是你发的"的标准答案。

## 七、订单状态机

```
CREATED(0) ──支付回调──▶ PAID(1) ──发货消费──▶ DELIVERED(2)
    │
    └──15分钟延迟消息──▶ CLOSED(3)   （active_flag 置 NULL，释放限购）
守卫：仅 CREATED→PAID 允许；重复回调被 状态机+pay_notify_log 唯一约束 双拦
充值单：CREATED→PAID 即到账（无发货）
```

## 八、接口清单（25 个，REST，Knife4j/springdoc 自动文档化）

**玩家端（JWT）**
| # | 接口 | 说明 |
|---|---|---|
| 1-2 | POST /api/auth/register · /login | 注册（BCrypt）/登录（发 JWT） |
| 3-4 | GET /api/goods/list · /api/goods/{id} | 四态货架（缓存）/详情 |
| 5 | POST /api/purchase/{goodsId} | 抢购：Lua 扣减+判重+发 ORDER_CREATED |
| 6 | GET /api/order/my | 我的订单（状态流转可见） |
| 7 | POST /api/recharge | 发起充值→返回假支付页跳转参数 |
| 8 | GET /api/bag · 14 GET /api/wallet | 背包 / 余额与流水 |
| 9-11 | POST /api/gacha/draw {count:1\|10} · GET records · GET pool | 抽卡 / 记录 / 概率公示+当前保底计数 |
| 12-13 | GET /api/rank/top · /api/rank/me | 前 100 / 我的排名 |

**假支付公司（mock-payment:18090）**
| # | 接口 | 说明 |
|---|---|---|
| 15 | GET /pay.html | 支付确认页（订单信息+确认按钮） |
| 16 | POST /mock-pay/confirm | 按作恶开关执行：正常/延迟30秒/重发3次 回调 |

**支付回调（主服务，供 mock-payment 调，HMAC 验签）**
| # | 接口 | 说明 |
|---|---|---|
| 25 | POST /api/pay/callback | 验签→pay_notify_log 记账→幂等判定→状态机流转→发 DELIVER_GOODS |

**管理端（X-Admin-Token）**
| # | 接口 | 说明 |
|---|---|---|
| 17-18 | POST /api/admin/login · POST goods | 运营登录 / 商品上架 |
| 19-20 | GET orders · GET pay-notifies | 订单流水 / 通知日志（幂等证据页） |
| 21-23 | GET recon · POST recon/run · POST mock-switch | 对账报表 / 手动触发 / 作恶开关切换 |

## 九、鉴权三层

1. 玩家端：JWT（Bearer），拦截器解析注入 userId；
2. 管理端：X-Admin-Token（配置文件固定值）；
3. 回调：HMAC-SHA256 验签（信任级别最低的入口，另配独立限流参数）。

## 十、接口文档方案

springdoc-openapi 自动生成（/v3/api-docs + swagger-ui），knife4j 作为中文皮肤增强。
引入顺序：先 springdoc 验证 Boot 4 兼容 → 再叠加 knife4j；任一不兼容则降级纯 springdoc。

## 十一、设计到循环的映射

循环 1 用表：user；循环 2：item/goods(+缓存键)；循环 3：stock_flow/order_info(+stock/buyflag 键+ORDER_CREATED)；循环 4：pay_notify_log(+mock-payment 进程)；循环 5：deliver_flow/bag；循环 6：延迟消息；循环 7：recon_diff；循环 8：gacha 三表/user_pity；循环 9：rank:main；循环 10：压测。每循环的自动化测试对应 PRD 场景表行号。
