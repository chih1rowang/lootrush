package com.lootrush.reconciliation;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ReconDiffMapper {

    @Insert("INSERT INTO recon_diff(recon_date, diff_type, order_no, detail, status) " +
            "VALUES(#{reconDate}, #{diffType}, #{orderNo}, #{detail}, #{status})")
    int insert(ReconDiffEntity diff);

    @Select("SELECT * FROM recon_diff ORDER BY id DESC LIMIT #{limit}")
    List<ReconDiffEntity> selectRecent(@Param("limit") int limit);

    /** 测试清理用 */
    @Delete("DELETE FROM recon_diff WHERE order_no = #{ref}")
    int deleteByOrderNo(@Param("ref") String ref);
}
