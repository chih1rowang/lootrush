package com.lootrush.trade;

import com.lootrush.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 背包接口（JWT 保护） */
@RestController
@RequestMapping("/api")
public class BagController {

    private final BagMapper bagMapper;

    public BagController(BagMapper bagMapper) {
        this.bagMapper = bagMapper;
    }

    @GetMapping("/bag")
    public ApiResponse<List<BagFlat>> myBag(HttpServletRequest request) {
        return ApiResponse.ok(bagMapper.selectByUser((Long) request.getAttribute("userId")));
    }
}
