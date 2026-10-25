# 事故报告 №004：Zipkin 收不到任何行程单（Boot 4 隐性装配缺口）

- 日期：2026-10-25
- 严重程度：中（观测三窗口之一完全失效，且无任何报错提示）
- 状态：已修复

## 1. 现象

程序正常运行业务，但 Zipkin（行程单档案室）的网页里服务名单永远是空的。
全程没有任何报错、没有 WARN 日志，属于"静默失效"。

## 2. 复现步骤

Spring Boot 4 + 按网上 Boot 3 教程配置（依赖 zipkin-reporter-brave +
yml 写 management.zipkin.tracing.endpoint），启动后随便刷多少次接口，
Zipkin 服务名单为空。

## 3. 排查过程（本案的精华，完整侦探链）

1. 排除 Zipkin 服务端问题：直接 POST 空数据到收件端点，返回 202（收件正常）。
2. 排除观测管线断裂：Prometheus 指标里 http_server_requests 正常产出
   ——说明"观测"在发生，是"追踪导出"断了。
3. 排除采样率太低：连刷 40+20+15 次请求零命中，10% 采样下概率仅 0.2%。
4. `--debug` 装配判定书：Zipkin 相关自动配置类显示 "matched"（在岗）。
5. /actuator/beans 人事档案：没有任何 sender（运输车）和 handler（发报机）bean
   ——判定书说在岗、人事档案说没人，矛盾指向 bean 方法级的隐藏条件。
6. /actuator/env 与 configprops 对比：yml 里的配置键被读到（env 有），
   但没有属性类消费它（configprops 无条目）——Boot 4 属性体系有变化。
7. 反编译 ZipkinWithBraveTracingAutoConfiguration：发报机的 @Bean 方法
   标着 @ConditionalOnBean——要求先存在运输车（BytesMessageSender）。
8. 全仓库搜索确认：Boot 4 所有官方包里都不再自动创建运输车。
9. 手写运输车 bean → 启动失败，暴露第二个缺口：还缺编码原料（BytesEncoder）
   → 再补 Encoding bean → 启动成功，行程单落地，服务名单出现 "flash-mall"。

### 案中案：僵尸进程污染实验

排查中途连续两轮实验"修复无效"实为误判：用 pkill 杀 mvnw 启动器时，
真正的 java 子进程命令行不含关键字而漏网，继续占着测试端口用旧代码应答。
后续实验全部打在僵尸身上。教训：杀后台 Java 进程，先 netstat 按端口找 PID，
再 taskkill 按 PID 处决，并验证端口确实释放。

## 4. 根因

Spring Boot 4 大重构：不再自动装配 Zipkin 的 sender 与编码零件，
改为要求使用者自备（同时 zipkin-reporter 3.x 也把具体运输实现拆成了独立包）。
双重变化叠加，所有基于 Boot 3 的教程配置静默失效。

## 5. 修复

新增 config/ZipkinSenderConfig：
- `zipkinSender`：URLConnectionSender，收件地址从配置读，默认 localhost:9411；
- `zipkinEncoding`：Encoding.JSON。
pom 补 zipkin-sender-urlconnection 依赖。

## 6. 预防

- 升级到新的大版本（Boot 3→4）集成中间件时，先读官方迁移说明，
  不默认上一版的自动装配行为还在；
- "静默失效"类问题的标准打法：装配判定书（--debug）看类级、
  beans 档案看实例级，两级对不上就反编译看 bean 方法的隐藏条件。
