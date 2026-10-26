#!/bin/bash
# LootRush 循环6 验收脚本（全自动版）：脚本自己起两个带验收配置的实例，跑完全自动清场
# 用法：bash docs/scripts/verify_loop6.sh
# 说明：你的 IDEA 主程序(8080)无需任何改动。本脚本临时启动 8081/8082 两个实例：
#       ORDER_CLOSE_DELAY_LEVEL=3(10秒延迟档) + ORDER_CLOSE_MINUTES=0 + ORDER_CLOSE_SCAN_SECONDS=10
#       两实例共同竞争扫表锁=分布式锁的真双实例演示。结束自动清理。

BASE=http://localhost:8081     # 主戏实例
OTHER=http://localhost:8082    # 对手实例（锁竞争者）
LOG1=/tmp/lootrush-8081.log
LOG2=/tmp/lootrush-8082.log

cleanup() {
  for P in 8081 8082; do
    PID=$(netstat -ano | grep "LISTENING" | grep ":$P" | head -1 | awk '{print $NF}')
    [ -n "$PID" ] && taskkill //F //PID $PID > /dev/null 2>&1
  done
}
trap cleanup EXIT

echo "== 0. 清理可能的残留实例并启动两个验收实例 =="
cleanup
sleep 2
SERVER_PORT=8081 ORDER_CLOSE_DELAY_LEVEL=3 ORDER_CLOSE_MINUTES=0 ORDER_CLOSE_SCAN_SECONDS=10 \
  ./mvnw -q spring-boot:run > $LOG1 2>&1 &
SERVER_PORT=8082 ORDER_CLOSE_DELAY_LEVEL=3 ORDER_CLOSE_MINUTES=0 ORDER_CLOSE_SCAN_SECONDS=10 \
  ./mvnw -q spring-boot:run > $LOG2 2>&1 &
echo "   两个实例启动中（各需约30-60秒）..."
for i in $(seq 1 90); do
  sleep 2
  curl -s -m 2 $BASE/ping > /dev/null 2>&1 && curl -s -m 2 $OTHER/ping > /dev/null 2>&1 && break
done
echo "   8081: $(curl -s -m 3 $BASE/ping)   8082: $(curl -s -m 3 $OTHER/ping)"

echo "== 1. 登录运营与玩家（经由8081）=="
ADMIN_TOKEN=$(curl -s -X POST $BASE/api/admin/login -H "Content-Type: application/json" -d '{"password":"admin123"}' | grep -oE '"token":"[^"]+"' | cut -d'"' -f4)
curl -s -X POST $BASE/api/auth/register -H "Content-Type: application/json" -d '{"username":"player1","password":"pass123"}' > /dev/null
TOKEN=$(curl -s -X POST $BASE/api/auth/login -H "Content-Type: application/json" -d '{"username":"player1","password":"pass123"}' | grep -oE '"token":"[^"]+"' | cut -d'"' -f4)
echo "   手环长度=${#TOKEN}"

echo "== 2. 上架库存2件的商品（1分钟后开售）=="
START=$(date -d '+1 minutes' +%Y-%m-%dT%H:%M:%S)
END=$(date -d '+2 hours' +%Y-%m-%dT%H:%M:%S)
GOODS_ID=$(curl -s -X POST $BASE/api/admin/goods -H "Content-Type: application/json" -H "X-Admin-Token: $ADMIN_TOKEN" -d "{\"name\":\"Loop6Test\",\"description\":\"loop6\",\"price\":10,\"originalPrice\":20,\"stock\":2,\"saleStartTime\":\"$START\",\"saleEndTime\":\"$END\"}" | grep -oE '"goodsId":[0-9]+' | grep -oE '[0-9]+')
echo "   商品ID=$GOODS_ID，等70秒开售..."
sleep 70

echo "== 3. 抢购但不付款 =="
ORDER_NO=$(curl -s -X POST $BASE/api/purchase/$GOODS_ID -H "Authorization: Bearer $TOKEN" | grep -oE '"orderNo":"[^"]+"' | cut -d'"' -f4)
echo "   订单=$ORDER_NO（故意不付款）"
echo "   此刻库存（应为1）："
docker exec mall-redis redis-cli GET "stock:$GOODS_ID"

echo "== 4. 等 30 秒：延迟消息(10s)或扫表(每10s)都会收割这张僵尸单 =="
sleep 30

echo "== 5. 验收点 =="
echo "-- 5.1 订单应已自动关闭（status=3, activeFlag=null）--"
curl -s $BASE/api/order/my -H "Authorization: Bearer $TOKEN" | grep -oE "\{[^{]*$ORDER_NO[^}]*\}"
echo "-- 5.2 库存应回补到 2 --"
docker exec mall-redis redis-cli GET "stock:$GOODS_ID"
echo "-- 5.3 限购应已释放：同一玩家再抢同一商品（应拿到新订单号）--"
curl -s -X POST $BASE/api/purchase/$GOODS_ID -H "Authorization: Bearer $TOKEN"; echo
echo "-- 5.4 库存流水（应有 CLOSE_BACK 回补记录）--"
docker exec mall-mysql mysql -uroot -p123456 lootrush -e "SELECT goods_id,change_count,reason FROM stock_flow WHERE order_no='$ORDER_NO';" 2>/dev/null

echo "== 6. 分布式锁证据：两实例的扫表日志（一边干活/一边跳过）=="
echo "-- 8081 --"
grep -a "扫表" $LOG1 | tail -3
echo "-- 8082 --"
grep -a "扫表" $LOG2 | tail -3

echo "验收完成：5.1=已关闭、5.2=2、5.3=再抢成功、5.4=CLOSE_BACK、6=两实例锁竞争痕迹 → 循环6过"
echo "（脚本退出时自动关闭两个临时实例；你的 8080 全程未受影响）"
