#!/bin/bash
# LootRush 循环4 验收脚本
# 用法：bash docs/scripts/verify_loop4.sh
# 前提：主程序(8080)与假支付公司(18090)都在运行。

BASE=http://localhost:8080
MOCK=http://localhost:18090
SECRET=lootrush-pay-hmac-secret-2026-0912

echo "== 1. 登录运营与玩家 =="
ADMIN_TOKEN=$(curl -s -X POST $BASE/api/admin/login -H "Content-Type: application/json" -d '{"password":"admin123"}' | grep -oE '"token":"[^"]+"' | cut -d'"' -f4)
curl -s -X POST $BASE/api/auth/register -H "Content-Type: application/json" -d '{"username":"player1","password":"pass123"}' > /dev/null
TOKEN=$(curl -s -X POST $BASE/api/auth/login -H "Content-Type: application/json" -d '{"username":"player1","password":"pass123"}' | grep -oE '"token":"[^"]+"' | cut -d'"' -f4)
echo "   手环长度=${#TOKEN}"

echo "== 2. 充值500钻石（走假支付）=="
R=$(curl -s -X POST $BASE/api/pay/recharge -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" -d '{"amount":500}')
RCNO=$(echo "$R" | grep -oE '"orderNo":"[^"]+"' | cut -d'"' -f4)
echo "   充值单=$RCNO"
echo "   [即时] 余额应+500："
curl -s $MOCK/mock-pay/switch > /dev/null
curl -s -X POST $MOCK/mock-pay/switch?mode=NORMAL > /dev/null
curl -s -X POST "$MOCK/mock-pay/confirm?orderNo=$RCNO&amount=500"; echo
sleep 1
curl -s $BASE/api/me -H "Authorization: Bearer $TOKEN"; echo

echo "== 3. 上架一件2分钟后开售的商品并抢购 =="
START=$(date -d '+2 minutes' +%Y-%m-%dT%H:%M:%S)
END=$(date -d '+2 hours' +%Y-%m-%dT%H:%M:%S)
GOODS_ID=$(curl -s -X POST $BASE/api/admin/goods -H "Content-Type: application/json" -H "X-Admin-Token: $ADMIN_TOKEN" -d "{\"name\":\"Loop4Test\",\"description\":\"loop4\",\"price\":50,\"originalPrice\":100,\"stock\":9,\"saleStartTime\":\"$START\",\"saleEndTime\":\"$END\"}" | grep -oE '"goodsId":[0-9]+' | grep -oE '[0-9]+')
echo "   商品ID=$GOODS_ID，等待130秒开售..."
sleep 130
ORDER_NO=$(curl -s -X POST $BASE/api/purchase/$GOODS_ID -H "Authorization: Bearer $TOKEN" | grep -oE '"orderNo":"[^"]+"' | cut -d'"' -f4)
echo "   订单=$ORDER_NO"

echo "== 4. REPEAT 作恶：同一条付款通知6秒内到达3次 =="
curl -s -X POST $MOCK/mock-pay/switch?mode=REPEAT > /dev/null
curl -s -X POST "$MOCK/mock-pay/confirm?orderNo=$ORDER_NO&amount=50"; echo
echo "   等8秒让3次通知都到达..."
sleep 8
curl -s -X POST $MOCK/mock-pay/switch?mode=NORMAL > /dev/null

echo "== 5. 验收点 =="
echo "-- 5.1 我的余额（初始1000+500充值-50付款=1450。若REPEAT扣了3次会是1350）--"
curl -s $BASE/api/me -H "Authorization: Bearer $TOKEN"; echo
echo "-- 5.2 我的订单（两单都应 status=1 已支付）--"
curl -s $BASE/api/order/my -H "Authorization: Bearer $TOKEN"; echo
echo "-- 5.3 支付通知日志（REPEAT那条notify_no只应有1行PROCESSED）--"
docker exec mall-mysql mysql -uroot -p123456 lootrush -e "SELECT notify_no,order_no,result FROM pay_notify_log ORDER BY id DESC LIMIT 5;" 2>/dev/null

echo "验收完成：余额=1450、两单status=1、通知日志无重复 → 循环4过"
