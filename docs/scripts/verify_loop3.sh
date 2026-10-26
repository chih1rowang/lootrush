#!/bin/bash
# LootRush 循环3 验收脚本
# 用法：Git Bash 里执行  bash docs/scripts/verify_loop3.sh
# 前提：主程序已在 8080 运行。脚本会自动上架一件"2分钟后开售"的商品再走抢购全流程。

BASE=http://localhost:8080

echo "== 1. 上架测试品（2分钟后开售，库存5）=="
ADMIN_TOKEN=$(curl -s -X POST $BASE/api/admin/login -H "Content-Type: application/json" -d '{"password":"admin123"}' | grep -oE '"token":"[^"]+"' | cut -d'"' -f4)
START=$(date -d '+2 minutes' +%Y-%m-%dT%H:%M:%S)
END=$(date -d '+2 hours' +%Y-%m-%dT%H:%M:%S)
GOODS_ID=$(curl -s -X POST $BASE/api/admin/goods -H "Content-Type: application/json" -H "X-Admin-Token: $ADMIN_TOKEN" -d "{\"name\":\"Loop3Test\",\"description\":\"loop3\",\"price\":10,\"originalPrice\":20,\"stock\":5,\"saleStartTime\":\"$START\",\"saleEndTime\":\"$END\"}" | grep -oE '"goodsId":[0-9]+' | grep -oE '[0-9]+')
echo "   商品ID=$GOODS_ID"
if [ -z "$GOODS_ID" ]; then
  echo "   上架失败，请检查主程序是否在运行，退出。"
  exit 1
fi

echo "== 2. 注册/登录玩家 =="
curl -s -X POST $BASE/api/auth/register -H "Content-Type: application/json" -d '{"username":"player1","password":"pass123"}' > /dev/null
TOKEN=$(curl -s -X POST $BASE/api/auth/login -H "Content-Type: application/json" -d '{"username":"player1","password":"pass123"}' | grep -oE '"token":"[^"]+"' | cut -d'"' -f4)
echo "   手环长度=${#TOKEN}"

echo "== 3. 等 130 秒让商品开售（别关窗口）=="
sleep 130

echo "== 4. 抢购四连 =="
echo "-- 4.1 首抢（期望返回 orderNo）--"
curl -s -X POST $BASE/api/purchase/$GOODS_ID -H "Authorization: Bearer $TOKEN"; echo

echo "-- 4.2 Redis 库存牌（期望 4）--"
docker exec mall-redis redis-cli GET "stock:$GOODS_ID"

echo "-- 4.3 再抢（期望 '每人限购一份'）--"
curl -s -X POST $BASE/api/purchase/$GOODS_ID -H "Authorization: Bearer $TOKEN"; echo

echo "-- 4.4 我的订单（期望一张 status=0 的订单）--"
sleep 5
curl -s $BASE/api/order/my -H "Authorization: Bearer $TOKEN"; echo

echo "== 5. Redis 判重标记（应有一条 buyflag:*）=="
docker exec mall-redis redis-cli KEYS 'buyflag:*'

echo "== 6. 对照组：不带手环抢购（期望 401 未登录）=="
curl -s -X POST $BASE/api/purchase/$GOODS_ID; echo

echo "验收完成：4.1=orderNo、4.2=4、4.3=限购拒绝、4.4=一张待支付订单、5=有标记、6=401 → 循环3过"
