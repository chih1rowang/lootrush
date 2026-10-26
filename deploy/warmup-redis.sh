#!/bin/bash
# Redis 库存预热：以数据库为准重建 stock:{goodsId}（事故007的修复工具，循环7对账的思想预演）
# 用法：bash deploy/warmup-redis.sh
set -e
docker exec mall-mysql mysql -uroot -p123456 lootrush -N -e "SELECT id, stock_total FROM goods;" 2>/dev/null | while read id total; do
  docker exec mall-redis redis-cli SET "stock:$id" "$total" > /dev/null
  echo "stock:$id = $total"
done
echo "预热完成"
