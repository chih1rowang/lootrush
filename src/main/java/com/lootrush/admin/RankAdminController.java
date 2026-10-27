package com.lootrush.admin;

import com.lootrush.common.ApiResponse;
import com.lootrush.rank.RankService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 管理端排行榜工具：历史回填 + 造数（X-Admin-Token 保护） */
@RestController
@RequestMapping("/api/admin/rank")
public class RankAdminController {

    private final RankService rankService;

    public RankAdminController(RankService rankService) {
        this.rankService = rankService;
    }

    @PostMapping("/backfill")
    public ApiResponse<Map<String, Integer>> backfill() {
        return ApiResponse.ok(Map.of("backfilled", rankService.backfillRealPlayers()));
    }

    @PostMapping("/seed")
    public ApiResponse<Map<String, Object>> seed(@RequestParam(defaultValue = "300000") int count) {
        long cost = rankService.seedBots(Math.min(count, 1_000_000));
        return ApiResponse.ok(Map.of("seeded", count, "costMs", cost));
    }
}
