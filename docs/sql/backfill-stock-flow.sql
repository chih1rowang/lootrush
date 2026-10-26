-- 库存流水补录（事故008附带的迁移工具修正版）
-- 用途：为"真实购买但缺失SALE流水"的订单补账（如旧代码消费窗口期的订单）
-- 注意：必须排除合成订单（RECONGAP%等直接插库的验收用假单）——
--       它们从未真实扣减库存，补了SALE就是幻影账（008补记的教训）
INSERT IGNORE INTO stock_flow(goods_id, change_count, reason, order_no)
SELECT o.goods_id, -1, 'SALE', o.order_no
FROM order_info o
WHERE o.type = 'PURCHASE'
  AND o.order_no LIKE 'P%'          -- 只有走正常抢购链路的订单才可能有真实销售
  AND NOT EXISTS (SELECT 1 FROM stock_flow s WHERE s.order_no = o.order_no AND s.reason = 'SALE');
