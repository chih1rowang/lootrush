package com.lootrush.gacha;

import com.lootrush.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 抽卡接口（JWT 保护）：抽、看卡池公示、看记录 */
@RestController
@RequestMapping("/api/gacha")
@Validated
public class GachaController {

    private final GachaService gachaService;

    public GachaController(GachaService gachaService) {
        this.gachaService = gachaService;
    }

    public record DrawReq(@NotNull @Min(1) @Max(10) Integer count) {
    }

    @PostMapping("/draw")
    public ApiResponse<GachaService.DrawResult> draw(@Validated @RequestBody DrawReq req,
                                                     HttpServletRequest request) {
        return ApiResponse.ok(gachaService.draw((Long) request.getAttribute("userId"), req.count()));
    }

    @GetMapping("/pool")
    public ApiResponse<GachaService.PoolVO> pool(HttpServletRequest request) {
        return ApiResponse.ok(gachaService.poolOf((Long) request.getAttribute("userId")));
    }

    @GetMapping("/records")
    public ApiResponse<List<GachaRecordFlat>> records(HttpServletRequest request) {
        return ApiResponse.ok(gachaService.recordsOf((Long) request.getAttribute("userId")));
    }
}
