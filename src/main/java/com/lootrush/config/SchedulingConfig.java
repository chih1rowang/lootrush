package com.lootrush.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 开启定时任务支持（扫表兜底任务依赖） */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
