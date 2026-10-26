# 事故报告 №007：应用连了一个月的 Redis 竟然是旧项目的（端口冲突静默降级）

- 日期：2026-10-26
- 严重程度：高（地基性错误：数据落在错误实例，旧容器一停数据即悬空）
- 状态：已修复

## 1. 现象

循环 6 验收脚本中 `docker exec mall-redis redis-cli GET stock:114` 输出为空，
但业务一切正常（抢购/回补/缓存全在工作）——"工具看不到数据，数据却活着"。

## 2. 复现步骤

宿主机执行 `docker exec mall-redis redis-cli KEYS '*'` → 几乎为空；
`docker exec gateflow-redis redis-cli KEYS 'stock:*'` → 我们项目的全部键都在。

## 3. 排查过程

1. `docker port mall-redis` → 空：mall-redis **没有宿主机端口映射**（docker-compose.yml
   里明明写了 6379:6379）——回溯部署日志：首次 `docker compose up` 时 6379 已被
   gateflow-redis 占用，mall-redis 端口绑定失败被静默跳过（当日只处理了 3306 的同类冲突，
   6379 的被 tail 截断漏看）；
2. `docker ps` 端口列：6379 归 `gateflow-redis (127.0.0.1:6379->6379)`；
3. 主程序连 localhost:6379 → 实际命中 gateflow-redis；
4. 铁证：`gateflow-redis GET stock:114 = 2`（正是循环 6 回补后的值），
   `mall-redis GET stock:114 = nil`。

## 4. 根因

部署日两个项目争用 6379：mall-redis 绑定失败未被察觉，应用经由宿主机 6379
连到了旧项目 gateflow-redis。此后**所有 Redis 数据（库存/缓存/判重/锁）一直
写在旧项目的实例里**。教训：端口冲突不能只修报错的那一个——`docker ps` 的
Ports 列必须逐行核对"预期映射"与"实际映射"。

## 5. 修复

1. `docker stop gateflow-redis`（旧项目暂缓中，容器保留可随时复活）；
2. `docker compose up -d --force-recreate redis` → mall-redis 取得 6379 映射；
3. 数据迁移采用"以数据库为准重新预热"：遍历 goods 表将 stock:{id} 重置为 stock_total
   （缓存与库存键本就是可重建数据，库是事实源——循环 7 对账思想的第一次实战）；
4. 验证：应用 Redis 连接池自动重连，/actuator/health 的 redis 项 UP，预热键抽查正确。

## 6. 预防

- README 端口登记表增加"实测 docker port"列，每次部署后核对；
- 中间件迁移/重建后，跑一遍"以数据库为准"的预热脚本（deploy/warmup-redis.sh）；
- gateflow 项目若复活需完整起 Redis，届时本项目的 Redis 端口需另行规划（登记表已标注）。
