# flash-mall 限时商城·抢购与抽卡后端

模拟手游"限时商城"运营活动的后端系统：瞬时洪峰下库存不超发、资金与道具全程可对账。

## 目录结构

```
flash-mall/
├── src/                  # Java 代码（业务逻辑都在这里）
├── deploy/               # 中间件启动配置（docker compose 一键起全家）
├── docs/PRD.md           # 项目总文档（PRD v2.0，一切以它为准）
├── docs/guides/          # 人话导览文档
├── docs/incidents/       # 事故报告（重大 bug 的发现、排查、修复记录）
├── pom.xml               # 项目零件清单
└── README.md             # 本文件
```

## 启动顺序

1. 起中间件（只需一次，之后开机自启或手动 start）：
   ```bash
   cd deploy
   docker compose up -d
   docker ps   # 应看到 8 个容器状态 Up
   ```
2. 用 IDEA 打开 flash-mall 文件夹，等右下角依赖下载完，点绿色三角运行。
3. 验证：浏览器打开 http://localhost:8080/ping 看到 pong。

## 观测系统入口

| 看什么 | 地址 |
|---|---|
| 程序体检（活着吗） | http://localhost:8080/actuator/health |
| Prometheus 指标原始数据 | http://localhost:8080/actuator/prometheus |
| 链路追踪（请求行程单） | http://localhost:9411 |
| 监控曲线（Grafana） | http://localhost:3000（admin/admin） |
| 消息队列控制台 | http://localhost:8180 |

## 本项目端口登记表（防止项目间互相踩踏）

| 端口 | 用途 | 备注 |
|---|---|---|
| 8080 | flash-mall 程序本身 | 曾被 gateflow-conductor 占用，已停（见 incidents/003） |
| 3308 | 商城 MySQL | 3306/3307 被旧项目占用，见 incidents/001 |
| 6379 | Redis | |
| 9876 / 10911 | RocketMQ 登记 / 仓库 | |
| 8180 | RocketMQ 控制台 | 镜像内部端口是 8082，见 incidents/001 |
| 9411 | Zipkin | |
| 9090 / 3000 | Prometheus / Grafana | |
| 18080 / 8888 / 3306 / 3307 / 5000 | 旧项目（student-course、gateflow） | 别动；conductor 已停，复活用 docker start gateflow-conductor |

## 规矩（写给未来的自己）

- 每个重大 bug：照 `docs/incidents/000-事故报告模板.md` 写报告，编号递增。
- 每个功能：一次 git 提交，说明写清"改了什么、为什么"。
- 每个技术点：必须配一个"亲眼看"动作（开哪个页面 / 敲哪条命令）。
