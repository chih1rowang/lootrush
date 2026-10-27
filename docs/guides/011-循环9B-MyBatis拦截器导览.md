# 导览 011：循环 9B —— 自定义 MyBatis 拦截器（SQL 耗时监控）

> 讲法同前：比喻搭脚手架，紧跟规范表述。

## 这一循环做了什么

给全项目的 SQL 通道设了一道"计费闸机"：每条 SQL 执行时自动掐表，
超过阈值（默认 300ms）的打 WARN 级"慢SQL"日志（带完整语句），正常的只留 DEBUG。
循环 10 压测时把阈值调低，慢 SQL 自动排队现身——这就是"工具自研、压测自用"。

## 回答"什么是 MyBatis 插件"

MyBatis 的**插件（Plugin）机制 = 官方提供的动态代理扩展点**：它允许第三方对
四个核心工位（执行器 Executor、语句处理器 StatementHandler、参数处理器
ParameterHandler、结果集处理器 ResultSetHandler）生成代理对象，
每次方法调用都先经过你的 intercept()——像在流水线工位设卡。
本项目的卡设在 **StatementHandler 的 query/update**（真正发 JDBC 的那一步），
因此全项目每一条 SQL（MyBatis 写的）都会被掐表，一网打尽。

## 技术要点

- `@Intercepts + @Signature` 声明拦谁：类型/方法/参数签名三件套；
- `@Component` 即生效：mybatis-spring-boot-starter 会自动收集容器里的
  Interceptor Bean 挂进 SqlSessionFactory（不需要任何额外注册代码）；
- `Plugin.wrap(target, this)` 只代理目标工位，非目标对象原样放行；
- 耗时统计放在 **finally** 块：SQL 抛异常也必须记录（异常 SQL 往往正是要找的）；
- 阈值可配置：`sql-trace.slow-ms`（默认 300，环境变量 SQL_SLOW_MS 覆盖）。

## 为什么自研而不用现成的 p6spy

p6spy 等现成方案要替换数据源驱动、带一串依赖；本拦截器三十行零依赖，
核心能力（耗时+语句+分级日志）完全等价，且输出格式可控、压测时可动态调阈值。
为简历一个词引一整套依赖是反向操作——**自研的前提是"它确实小"**。

## 测试的巧思：怎么验证"慢SQL真的会打 WARN"

让 SQL 真变慢需要 SLEEP 语句或改超时，都很别扭。换思路：**把阈值设为 0**
（测试专用配置 `sql-trace.slow-ms=0`），任何一条 SQL 都会被判"慢"；
再用 Logback 的 ListAppender（内存日志收集器）捕获拦截器的输出，
断言存在 WARN 级"慢SQL"。附带的第二条断言同样重要：**查询结果必须正常返回**
——拦截器只旁观不干预，这是插件的第一纪律。

## 简历行（技术栈之外的亮点行）

> 自定义 MyBatis 拦截器实现 SQL 耗时监控与慢查询告警（动态代理扩展点，阈值可配）

## 文件地图

| 文件 | 职责 |
|---|---|
| config/SqlTimingInterceptor.java | 拦截器本体（掐表/分级/阈值） |
| application.yml | sql-trace.slow-ms 阈值配置 |
| test/.../SqlTimingInterceptorTest.java | 阈值=0 + ListAppender 捕获断言 |
