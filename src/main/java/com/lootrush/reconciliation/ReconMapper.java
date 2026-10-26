package com.lootrush.reconciliation;

import com.lootrush.trade.OrderEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 对账三项核对的数据查询 */
@Mapper
public interface ReconMapper {

    /** 核对A：每件商品的数据库推导库存 = 上架总量 + 流水合计（SALE为-1/CLOSE_BACK为+1） */
    @Select("SELECT g.id AS goodsId, g.stock_total + IFNULL(SUM(s.change_count),0) AS expectedStock " +
            "FROM goods g LEFT JOIN stock_flow s ON s.goods_id = g.id " +
            "GROUP BY g.id, g.stock_total")
    List<ExpectedStockRow> selectExpectedStock();

    /** 核对B：已支付但没有任何发货记录的购买订单（发货消息疑似丢失） */
    @Select("SELECT * FROM order_info o WHERE o.type='PURCHASE' AND o.status=1 " +
            "AND NOT EXISTS (SELECT 1 FROM deliver_flow d WHERE d.order_no = o.order_no)")
    List<OrderEntity> selectPaidWithoutDeliver();

    /** 核对C：存在发货记录但订单状态不是已支付/已发货（数据异常，不自动修复） */
    @Select("SELECT d.order_no FROM deliver_flow d JOIN order_info o ON o.order_no = d.order_no " +
            "WHERE o.status NOT IN (1,2)")
    List<String> selectDeliveredWithoutPaid();
}
