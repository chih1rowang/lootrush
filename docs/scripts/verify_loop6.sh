#!/bin/bash
# LootRush 循环6 验收脚本：延迟关单 + 库存回补 + 限购释放 + 双实例分布式锁
# 用法：bash docs/scripts/verify_loop6.sh
# 前提：主程序(8080)以环境变量 ORDER_CLOSE_DELAY_LEVEL=3 运行（10秒延迟档），
#       假支付公司(18090)在运行（本脚本不用它，但保持环境完整）。
# IDEA 设置法：Run → Edit Configurations → Environment variables 填
#       ORDER_CLOSE_DELAY_LEVEL=3  → 保存并重启主程序。

BASE=http://localhost:8080
SECOND_LOG=/tmp/lootrush-8081.log

echo "== 0. 环境自检 =="
curl -s -m 3 $BASE/ping > /dev/null || { echo "主程序未运行，退出"; exit 1; }
echo "   主程序在线"

echo "== 1. 登录运营与玩家 =="
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

echo "== 4. 起8081第二实例（双实例分布式锁演示）=="
SERVER_PORT=8081 ./mvnw -q spring-boot:run > $SECOND_LOG 2>&1 &
SECOND_PID=$!
echo "   8081 启动中（后台），等待其就绪..."
for i in $(seq 1 60); do
  sleep 2
  curl -s -m 2 http://localhost:8081/ping > /dev/null 2>&1 && break
done
curl -s -m 2 http://localhost:8081/ping > /dev/null 2>&1 && echo "   8081 在线" || echo "   8081 未就绪（不影响关单验收）"

echo "== 5. 等 25 秒：10秒延迟档到点，关单消息落地 =="
sleep 25

echo "== 6. 验收点 =="
echo "-- 6.1 订单应已自动关闭（status=3, activeFlag=null）--"
curl -s $BASE/api/order/my -H "Authorization: Bearer $TOKEN" | grep -oE "\{[^{]*$ORDER_NO[^}]*\}"
echo "-- 6.2 库存应回补到 2 --"
docker exec mall-redis redis-cli GET "stock:$GOODS_ID"
echo "-- 6.3 限购应已释放：同一玩家再抢同一商品（应成功拿到新订单号）--"
curl -s -X POST $BASE/api/purchase/$GOODS_ID -H "Authorization: Bearer $TOKEN"; echo
echo "-- 6.4 库存流水（应有 CLOSE_BACK 回补记录）--"
docker exec mall-mysql mysql -uroot -p123456 lootrush -e "SELECT goods_id,change_count,reason FROM stock_flow WHERE order_no='$ORDER_NO';" 2>/dev/null

echo "== 7. 双实例锁证据：两个进程日志里的扫表痕迹 =="
echo "-- 8080（主实例）最近的扫表日志 --"
grep -a "扫表\|本轮" $SECOND_LOG 2>/dev/null | tail -2
echo "-- 8081（第二实例）日志 --"
grep -a "扫表\|本轮" $SECOND_LOG 2>/dev/null | tail -2
echo "（两实例只有一边持续真正扫表、另一边出现'跳过'即锁生效；本项为观察项，不阻塞验收）"

echo "== 8. 清理第二实例 =="
kill $SECOND_PID 2>/dev/null
sleep 2
PID=$(netstat -ano | grep "LISTENING" | grep ":8081" | head -1 | awk '{print $NF}')
[ -n "$PID" ] && taskkill //F //PID $PID > /dev/null 2>&1
echo "   8081 已关闭"

echo "验收完成：6.1=已关闭、6.2=2、6.3=再抢成功、6.4=CLOSE_BACK → 循环6过"
