#!/bin/bash
# LootRush 循环8 验收脚本：抽卡（概率公示/硬保底90/流水审计）
# 用法：bash docs/scripts/verify_loop8.sh
# 前提：主程序(8080)+假支付公司(18090)在运行。脚本自动充值9000钻并抽满90发验证硬保底。

BASE=http://localhost:8080
MOCK=http://localhost:18090

echo "== 0. 环境自检 =="
curl -s -m 3 $BASE/ping > /dev/null 2>&1 || { echo "主程序未运行"; exit 1; }
curl -s -m 3 $MOCK/mock-pay/switch > /dev/null 2>&1 || { echo "假支付公司未运行"; exit 1; }
echo "   在线"

echo "== 1. 登录玩家 =="
curl -s -X POST $BASE/api/auth/register -H "Content-Type: application/json" -d '{"username":"player1","password":"pass123"}' > /dev/null
TOKEN=$(curl -s -X POST $BASE/api/auth/login -H "Content-Type: application/json" -d '{"username":"player1","password":"pass123"}' | grep -oE '"token":"[^"]+"' | cut -d'"' -f4)
echo "   手环长度=${#TOKEN}"

echo "== 2. 卡池公示（应见三档概率89%/10%/1%与保底进度）=="
curl -s $BASE/api/gacha/pool -H "Authorization: Bearer $TOKEN"; echo

echo "== 3. 充值9000钻（9笔，走NORMAL即时回调）=="
curl -s -X POST $MOCK/mock-pay/switch?mode=NORMAL > /dev/null
for i in $(seq 1 9); do
  R=$(curl -s -X POST $BASE/api/pay/recharge -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" -d '{"amount":1000}')
  RCNO=$(echo "$R" | grep -oE '"orderNo":"[^"]+"' | cut -d'"' -f4)
  curl -s -X POST "$MOCK/mock-pay/confirm?orderNo=$RCNO&amount=1000" > /dev/null
done
echo "   当前余额："
curl -s $BASE/api/me -H "Authorization: Bearer $TOKEN"; echo

echo "== 4. 十连抽一发看结构 =="
curl -s -X POST $BASE/api/gacha/draw -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" -d '{"count":10}' | head -c 600; echo

echo "== 5. 连抽到90发验证硬保底（再抽80发，单抽循环）=="
for i in $(seq 1 80); do
  curl -s -X POST $BASE/api/gacha/draw -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" -d '{"count":1}' > /dev/null
done
echo "   90发抽完"

echo "== 6. 验收点 =="
echo "-- 6.1 保底计数与出金统计（聚合审计）--"
docker exec mall-mysql mysql -uroot -p123456 lootrush -e "SELECT COUNT(*) AS total_pulls, SUM(rarity=3) AS gold_count, MAX(pity_count_after) AS max_pity, SUM(is_pity_hit) AS pity_hits FROM gacha_record WHERE user_id=5;" 2>/dev/null
echo "-- 6.2 最近5条流水（只增不改的原始账本）--"
curl -s $BASE/api/gacha/records -H "Authorization: Bearer $TOKEN" | head -c 400; echo
echo "-- 6.3 钱包流水（GACHA 类型应出现）--"
docker exec mall-mysql mysql -uroot -p123456 lootrush -e "SELECT change_type, amount, balance_after FROM wallet_flow WHERE user_id=5 AND change_type='GACHA' ORDER BY id DESC LIMIT 3;" 2>/dev/null

echo "验收判定：gold_count>=1（90发内必有金）、max_pity<=90、pity_hits>=1（若无自然出金）→ 循环8过"
