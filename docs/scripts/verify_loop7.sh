#!/bin/bash
# LootRush 循环7 验收脚本：对账三项核对与修复闭环
# 用法：bash docs/scripts/verify_loop7.sh
# 前提：主程序(8080)已 Rebuild+重启到循环7代码（对账接口是本循环新增的）。

BASE=http://localhost:8080

echo "== 1. 登录运营与玩家 =="
ADMIN_TOKEN=$(curl -s -X POST $BASE/api/admin/login -H "Content-Type: application/json" -d '{"password":"admin123"}' | grep -oE '"token":"[^"]+"' | cut -d'"' -f4)
curl -s -X POST $BASE/api/auth/register -H "Content-Type: application/json" -d '{"username":"player1","password":"pass123"}' > /dev/null
TOKEN=$(curl -s -X POST $BASE/api/auth/login -H "Content-Type: application/json" -d '{"username":"player1","password":"pass123"}' | grep -oE '"token":"[^"]+"' | cut -d'"' -f4)
echo "   手环长度=${#TOKEN}"

echo "== 2. 数据库推导库存（事实源）: 商品96 =="
docker exec mall-mysql mysql -uroot -p123456 lootrush -e "SELECT g.id, g.stock_total + IFNULL(SUM(s.change_count),0) AS expected FROM goods g LEFT JOIN stock_flow s ON s.goods_id=g.id WHERE g.id=96 GROUP BY g.id, g.stock_total;" 2>/dev/null

echo "== 3. 重新埋靶: Redis stock:96 = 999（模拟Redis与数据库漂移）=="
docker exec mall-redis redis-cli SET stock:96 999

echo "== 4. 手动触发对账 =="
curl -s -X POST $BASE/api/admin/recon/run -H "X-Admin-Token: $ADMIN_TOKEN"; echo

echo "== 5. 修复后 Redis stock:96（应等于步骤2的expected）=="
docker exec mall-redis redis-cli GET stock:96

echo "== 6. 差异报表（应出现 STOCK_MISMATCH / GOODS:96, status=FIXED）=="
curl -s $BASE/api/admin/recon/diffs -H "X-Admin-Token: $ADMIN_TOKEN" | head -c 500; echo

echo "== 7. 构造第二类差异：已支付未发货（模拟发货消息丢失）=="
GAP_NO="RECONGAP$(date +%s)"
docker exec mall-mysql mysql -uroot -p123456 lootrush -e "INSERT IGNORE INTO order_info(order_no,type,user_id,goods_id,price_diamond,status,active_flag) VALUES('$GAP_NO','PURCHASE',5,96,30,1,NULL);" 2>/dev/null
echo "   缺口订单 $GAP_NO 已注入（已支付、无发货记录）"
curl -s -X POST $BASE/api/admin/recon/run -H "X-Admin-Token: $ADMIN_TOKEN"; echo
echo "   等待重投的发货消息被消费者处理..."
sleep 6

echo "== 8. 验收点 =="
echo "-- 8.1 发货流水（应出现 $GAP_NO，qty=1）--"
docker exec mall-mysql mysql -uroot -p123456 lootrush -e "SELECT order_no,item_id,qty FROM deliver_flow WHERE order_no='$GAP_NO';" 2>/dev/null
echo "-- 8.2 背包（Loop5Blade 的 count 应比上一轮 +1）--"
curl -s $BASE/api/bag -H "Authorization: Bearer $TOKEN"; echo
echo "-- 8.3 差异表（两类差异都应 FIXED）--"
docker exec mall-mysql mysql -uroot -p123456 lootrush -e "SELECT diff_type,order_no,status FROM recon_diff WHERE order_no IN ('GOODS:96','$GAP_NO');" 2>/dev/null

echo "验收完成：5=expected、8.1有行、8.2 count增加、8.3两行FIXED → 循环7过"
