package com.lootrush.admin;

import com.lootrush.common.ApiResponse;
import com.lootrush.common.BizException;
import com.lootrush.goods.GoodsService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/** 管理端：运营登录、上架、改价（X-Admin-Token 鉴权，AdminInterceptor 把关） */
@RestController
@RequestMapping("/api/admin")
@Validated
public class AdminController {

    private final GoodsService goodsService;
    private final String adminPassword;
    private final String adminToken;

    public AdminController(GoodsService goodsService,
                           @Value("${admin.password}") String adminPassword,
                           @Value("${admin.token}") String adminToken) {
        this.goodsService = goodsService;
        this.adminPassword = adminPassword;
        this.adminToken = adminToken;
    }

    public record LoginReq(@NotBlank String password) {
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, String>> login(@Validated @RequestBody LoginReq req) {
        if (!adminPassword.equals(req.password())) {
            throw BizException.of(401, "管理密码错误");
        }
        return ApiResponse.ok(Map.of("token", adminToken));
    }

    /** 上架一件商品（时间用 ISO 格式，如 2026-10-26T20:00:00；稀有度1蓝/2紫，金色只能抽卡出） */
    public record CreateGoodsReq(
            @NotBlank(message = "商品名不能为空") String name,
            String description,
            String imageUrl,
            @NotNull(message = "稀有度不能为空")
            @Min(value = 1, message = "稀有度最低1") @Max(value = 2, message = "商城稀有度最高2，金色只能从卡池获得")
            Integer rarity,
            @NotNull @Min(value = 1, message = "售价至少1钻石") Integer price,
            @NotNull Integer originalPrice,
            @NotNull @Min(value = 1, message = "库存至少1件") Integer stock,
            @NotNull LocalDateTime saleStartTime,
            @NotNull LocalDateTime saleEndTime) {
    }

    @PostMapping("/goods")
    public ApiResponse<Map<String, Long>> createGoods(@Validated @RequestBody CreateGoodsReq req) {
        Long id = goodsService.createGoods(req.name(), req.description(), req.imageUrl(),
                req.rarity(), req.price(), req.originalPrice(), req.stock(),
                req.saleStartTime(), req.saleEndTime());
        return ApiResponse.ok(Map.of("goodsId", id));
    }

    public record PriceReq(@NotNull @Min(1) Integer price) {
    }

    @PutMapping("/goods/{id}/price")
    public ApiResponse<Void> updatePrice(@PathVariable Long id, @Validated @RequestBody PriceReq req) {
        goodsService.updatePrice(id, req.price());
        return ApiResponse.ok();
    }
}
