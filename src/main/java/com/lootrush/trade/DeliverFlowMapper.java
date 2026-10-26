package com.lootrush.trade;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DeliverFlowMapper {

    @Insert("INSERT INTO deliver_flow(order_no, user_id, item_id, qty) " +
            "VALUES(#{orderNo}, #{userId}, #{itemId}, #{qty})")
    int insert(DeliverFlowEntity flow);

    @Select("SELECT * FROM deliver_flow WHERE order_no = #{orderNo}")
    DeliverFlowEntity selectByOrderNo(@Param("orderNo") String orderNo);

    /** 测试清理用 */
    @org.apache.ibatis.annotations.Delete("DELETE FROM deliver_flow WHERE order_no = #{orderNo}")
    int deleteByOrderNo(@Param("orderNo") String orderNo);
}
