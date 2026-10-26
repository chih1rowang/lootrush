package com.lootrush.trade;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface OrderMapper {

    @Insert("INSERT INTO order_info(order_no, type, user_id, goods_id, price_diamond, status, active_flag) " +
            "VALUES(#{orderNo}, #{type}, #{userId}, #{goodsId}, #{priceDiamond}, #{status}, #{activeFlag})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(OrderEntity order);

    @Select("SELECT * FROM order_info WHERE order_no = #{orderNo}")
    OrderEntity selectByOrderNo(@Param("orderNo") String orderNo);

    @Select("SELECT * FROM order_info WHERE user_id = #{userId} ORDER BY create_time DESC")
    List<OrderEntity> selectByUser(@Param("userId") Long userId);

    /** 状态机守卫：只有待支付(0)→已支付(1)一条边可走；重复回调影响0行 */
    @Update("UPDATE order_info SET status = 1 WHERE order_no = #{orderNo} AND status = 0")
    int markPaidIfCreated(@Param("orderNo") String orderNo);

    /** 状态机守卫：只有已支付(1)→已发货(2)一条边可走；重复发货消息影响0行 */
    @Update("UPDATE order_info SET status = 2 WHERE order_no = #{orderNo} AND status = 1")
    int markDeliveredIfPaid(@Param("orderNo") String orderNo);

    /** 状态机守卫：只有待支付(0)→已关闭(3)一条边可走，同时释放限购（active_flag置NULL） */
    @Update("UPDATE order_info SET status = 3, active_flag = NULL WHERE order_no = #{orderNo} AND status = 0")
    int markClosedIfCreated(@Param("orderNo") String orderNo);

    /** 扫表兜底：找出超时未付的订单（分批，每轮最多100张） */
    @Select("SELECT * FROM order_info WHERE status = 0 AND create_time < #{cutoff} ORDER BY id LIMIT 100")
    List<OrderEntity> selectExpiredUnpaid(@Param("cutoff") java.time.LocalDateTime cutoff);

    /** 测试清理用 */
    @Delete("DELETE FROM order_info WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);
}
