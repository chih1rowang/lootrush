package com.lootrush.admin;

import com.lootrush.common.ApiResponse;
import com.lootrush.reconciliation.ReconDiffEntity;
import com.lootrush.reconciliation.ReconDiffMapper;
import com.lootrush.reconciliation.ReconService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 管理端对账接口（X-Admin-Token 保护）：手动触发 + 差异报表 */
@RestController
@RequestMapping("/api/admin/recon")
public class ReconAdminController {

    private final ReconService reconService;
    private final ReconDiffMapper reconDiffMapper;

    public ReconAdminController(ReconService reconService, ReconDiffMapper reconDiffMapper) {
        this.reconService = reconService;
        this.reconDiffMapper = reconDiffMapper;
    }

    /** 手动触发一轮对账（定时任务之外的人工入口） */
    @PostMapping("/run")
    public ApiResponse<ReconService.Summary> run() {
        return ApiResponse.ok(reconService.runOnce("手动"));
    }

    /** 最近50条差异记录（将来管理后台对账报表页的数据源） */
    @GetMapping("/diffs")
    public ApiResponse<List<ReconDiffEntity>> diffs() {
        return ApiResponse.ok(reconDiffMapper.selectRecent(50));
    }
}
