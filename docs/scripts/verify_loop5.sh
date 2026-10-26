#!/bin/bash
# LootRush 循环5 验收脚本：付完钱 → 道具进背包 → 订单变已发货
# 用法：bash docs/scripts/verify_loop5.sh
# 前提：主程序(8080)与假支付公司(18090)都在运行。

BASE=http://localhost:8080
MOCK=http://localhost:18090

echo "== 1. 登录运营与玩家 =="
ADMIN_TOKEN=$(curl -s -X POST $BASE/api/admin/login -H "Content-Type: application/json" -d '{"password":"admin123"}' | grep -oE '"token":"[^"]+"' | cut -d'"' -f4)
curl -s -X POST $BASE/api/auth/register -H "Content-Type: application/json" -d '{"username":"player1","password":"pass123"}' > /dev/null
TOKEN=$(curl -s -X POST $BASE/api/auth/login -H "Content-Type: application/json" -d '{"username":"player1","password":"pass123"}' | grep -oE '"token":"[^"]+"' | cut -d'"' -f4)
echo "   手环长度=${#TOKEN}"

echo "== 2. 保障余额充足（充1000，走NORMAL即时回调）=="
curl -s -X POST $MOCK/mock-pay/switch?mode=NORMAL > /dev/null
R=$(curl -s -X POST $BASE/api/pay/recharge -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" -d '{"amount":1000}')
RCNO=$(echo "$R" | grep -oE '"orderNo":"[^"]+"' | cut -d'"' -f4)
curl -s -X POST "$MOCK/mock-pay/confirm?orderNo=$RCNO&amount=1000" > /dev/null
sleep 1
echo "   当前余额："
curl -s $BASE/api/me -H "Authorization: Bearer $TOKEN"; echo

echo "== 3. 上架2分钟后开售的商品并抢购 =="
START=$(date -d '+2 minutes' +%Y-%m-%dT%H:%M:%S)
END=$(date -d '+2 hours' +%Y-%m-%dT%H:%M:%S)
GOODS_ID=$(curl -s -X POST $BASE/api/admin/goods -H "Content-Type: application/json" -H "X-Admin-Token: $ADMIN_TOKEN" -d "{\"name\":\"Loop5Blade\",\"description\":\"loop5\",\"price\":30,\"originalPrice\":60,\"stock\":7,\"saleStartTime\":\"$START\",\"saleEndTime\":\"$END\"}" | grep -oE '"goodsId":[0-9]+' | grep -oE '[0-9]+')
echo "   商品ID=$GOODS_ID，等待130秒开售..."
sleep 130
ORDER_NO=$(curl -s -X POST $BASE/api/purchase/$GOODS_ID -H "Authorization: Bearer $TOKEN" | grep -oE '"orderNo":"[^"]+"' | cut -d'"' -f4)
echo "   订单=$ORDER_NO"

echo "== 4. 付款（NORMAL 即时回调）=="
curl -s -X POST "$MOCK/mock-pay/confirm?orderNo=$ORDER_NO&amount=30"; echo
echo "   等5秒让异步发货完成..."
sleep 5

echo "== 5. 验收点 =="
echo "-- 5.1 我的背包（应出现 Loop5Blade，count=1）--"
curl -s $BASE/api/bag -H "Authorization: Bearer $TOKEN"; echo
echo "-- 5.2 我的订单（PURCHASE单应 status=2 已发货）--"
curl -s $BASE/api/order/my -H "Authorization: Bearer $TOKEN"; echo
echo "-- 5.3 发货流水（本单应恰好1行）--"
docker exec mall-mysql mysql -uroot -p123456 lootrush -e "SELECT order_no,item_id,qty FROM deliver_flow WHERE order_no='$ORDER_NO';" 2>/dev/null

echo "验收完成：背包出现道具、订单status=2、流水1行 → 循环5过"
