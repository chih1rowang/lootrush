#!/bin/bash
# LootRush 循环9 验收脚本：排行榜（真实消费计分/回填/30万造数/微秒查询）
# 用法：bash docs/scripts/verify_loop9.sh
# 前提：主程序(8080)已 Rebuild+重启到循环9代码。

BASE=http://localhost:8080

echo "== 0. 环境自检 =="
curl -s -m 3 $BASE/ping > /dev/null 2>&1 || { echo "主程序未运行"; exit 1; }
echo "   在线"

echo "== 1. 登录玩家 =="
ADMIN_TOKEN=$(curl -s -X POST $BASE/api/admin/login -H "Content-Type: application/json" -d '{"password":"admin123"}' | grep -oE '"token":"[^"]+"' | cut -d'"' -f4)
curl -s -X POST $BASE/api/auth/register -H "Content-Type: application/json" -d '{"username":"player1","password":"pass123"}' > /dev/null
TOKEN=$(curl -s -X POST $BASE/api/auth/login -H "Content-Type: application/json" -d '{"username":"player1","password":"pass123"}' | grep -oE '"token":"[^"]+"' | cut -d'"' -f4)
echo "   手环长度=${#TOKEN}"

echo "== 2. 历史消费回填（从钱包流水事实源重建真实玩家分数）=="
curl -s -X POST $BASE/api/admin/rank/backfill -H "X-Admin-Token: $ADMIN_TOKEN"; echo
echo "   player1 当前榜内数据（应为历史消费合计）："
curl -s $BASE/api/rank/me -H "Authorization: Bearer $TOKEN"; echo

echo "== 3. 灌30万测试玩家（测试数据工厂，pipeline批量）=="
curl -s -X POST "$BASE/api/admin/rank/seed?count=300000" -H "X-Admin-Token: $ADMIN_TOKEN"; echo
echo "   榜内总成员数："
docker exec mall-redis redis-cli ZCARD rank:main

echo "== 4. 30万成员下的查询性能（跳表规模故事的主角）=="
echo "-- 4.1 查前100耗时（秒）--"
curl -s -o /dev/null -w "%{time_total}\n" $BASE/api/rank/top
echo "-- 4.2 查我的排名耗时（秒）--"
curl -s -o /dev/null -w "%{time_total}\n" $BASE/api/rank/me -H "Authorization: Bearer $TOKEN"
echo "-- 4.3 前5名 --"
curl -s "$BASE/api/rank/top?limit=5" -H "Authorization: Bearer $TOKEN" | head -c 500; echo
echo "-- 4.4 我的排名 --"
curl -s $BASE/api/rank/me -H "Authorization: Bearer $TOKEN"; echo

echo "== 5. 实时计分闭环：player1 再抽卡一次（充1000→十连）→ 榜内分数应+1000 =="
R=$(curl -s -X POST $BASE/api/pay/recharge -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" -d '{"amount":1000}')
RCNO=$(echo "$R" | grep -oE '"orderNo":"[^"]+"' | cut -d'"' -f4)
curl -s -X POST "http://localhost:18090/mock-pay/confirm?orderNo=$RCNO&amount=1000" > /dev/null
sleep 1
BEFORE=$(curl -s $BASE/api/rank/me -H "Authorization: Bearer $TOKEN" | grep -oE '"score":[0-9]+' | grep -oE '[0-9]+')
curl -s -X POST $BASE/api/gacha/draw -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" -d '{"count":10}' > /dev/null
AFTER=$(curl -s $BASE/api/rank/me -H "Authorization: Bearer $TOKEN" | grep -oE '"score":[0-9]+' | grep -oE '[0-9]+')
echo "   抽卡前score=$BEFORE，抽卡后score=$AFTER"
[ "$((AFTER-BEFORE))" = "1600" ] && echo "   实时计分验证通过（十连1600钻）" || echo "   计分异常！"

echo "验收判定：回填有数、ZCARD>=300000、4.1<0.2秒、实时+1000 → 循环9过"
