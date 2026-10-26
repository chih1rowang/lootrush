package com.mockpay;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/** 作恶开关：NORMAL正常 / DELAY延迟30秒回调 / REPEAT同一通知重发3次 */
@Component
public class EvilSwitch {

    public enum Mode { NORMAL, DELAY, REPEAT }

    private final AtomicReference<Mode> current = new AtomicReference<>(Mode.NORMAL);

    @SuppressWarnings("unused")
    private void unusedConfigAnchor(@Value("${pay.secret}") String secret) {
        // 仅示意配置存在；secret 实际由 Sender 使用
    }

    public Mode get() {
        return current.get();
    }

    public void set(Mode mode) {
        current.set(mode);
    }
}
