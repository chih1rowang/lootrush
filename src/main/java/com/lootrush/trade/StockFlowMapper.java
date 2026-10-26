package com.lootrush.trade;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StockFlowMapper {

    @Insert("INSERT INTO stock_flow(goods_id, change_count, reason, order_no) " +
            "VALUES(#{goodsId}, #{changeCount}, #{reason}, #{orderNo})")
    int insert(StockFlowEntity flow);
}
