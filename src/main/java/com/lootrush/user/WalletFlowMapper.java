package com.lootrush.user;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WalletFlowMapper {

    @Insert("INSERT INTO wallet_flow(user_id, change_type, amount, balance_after, ref_order_no) " +
            "VALUES(#{userId}, #{changeType}, #{amount}, #{balanceAfter}, #{refOrderNo})")
    int insert(WalletFlowEntity flow);
}
