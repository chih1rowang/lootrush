package com.lootrush.rank;

import com.lootrush.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 排行榜接口（JWT 保护） */
@RestController
@RequestMapping("/api/rank")
public class RankController {

    private final RankService rankService;

    public RankController(RankService rankService) {
        this.rankService = rankService;
    }

    @GetMapping("/top")
    public ApiResponse<List<RankEntry>> top(@RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(rankService.top(Math.min(limit, 100)));
    }

    @GetMapping("/me")
    public ApiResponse<RankService.MeRank> me(HttpServletRequest request) {
        return ApiResponse.ok(rankService.me((Long) request.getAttribute("userId")));
    }
}
