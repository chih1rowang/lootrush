-- 原子扣减：一次网络往返内完成「判重 + 查库存 + 扣减」，执行期间不被其他命令插入。
-- KEYS[1] = stock:{goodsId}    KEYS[2] = buyflag:{goodsId}:{userId}
-- ARGV[1] = orderNo（作为判重标记的值）   ARGV[2] = 判重标记的过期秒数
-- 返回：0=成功；-1=已售罄；-2=库存未预热；-3=已抢购过（一人一单）
if redis.call('EXISTS', KEYS[2]) == 1 then
    return -3
end
local s = redis.call('GET', KEYS[1])
if not s then
    return -2
end
if tonumber(s) <= 0 then
    return -1
end
redis.call('DECR', KEYS[1])
redis.call('SET', KEYS[2], ARGV[1], 'EX', ARGV[2])
return 0
