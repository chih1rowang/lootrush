package com.lootrush.goods;

import com.lootrush.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 玩家端货架接口（需登录，JwtInterceptor 把关） */
@RestController
@RequestMapping("/api/goods")
public class GoodsController {

    private final GoodsService goodsService;

    public GoodsController(GoodsService goodsService) {
        this.goodsService = goodsService;
    }

    @GetMapping("/list")
    public ApiResponse<List<GoodsService.GoodsVO>> list() {
        return ApiResponse.ok(goodsService.listGoods());
    }
}
