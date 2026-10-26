package com.lootrush.trade;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PayNotifyLogMapper {

    @Insert("INSERT INTO pay_notify_log(notify_no, order_no, amount, result) " +
            "VALUES(#{notifyNo}, #{orderNo}, #{amount}, #{result})")
    int insert(PayNotifyLogEntity log);

    @Update("UPDATE pay_notify_log SET result = #{result} WHERE notify_no = #{notifyNo}")
    int updateResult(@Param("notifyNo") String notifyNo, @Param("result") String result);
}
