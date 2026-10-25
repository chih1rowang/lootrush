package com.lootrush;

import java.sql.Connection;
import javax.sql.DataSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据库连接自检接口：用和体检（/actuator/health）同一个连接池真实取一次连接。
 * 成功返回数据库版本；失败时把异常从外到里每一层的类型和消息都打印出来，
 * 用于定位"体检报 DOWN 但说不出原因"的问题。
 */
@RestController
public class DbCheckController {

    private final DataSource dataSource;

    public DbCheckController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/db-check")
    public String dbCheck() {
        StringBuilder sb = new StringBuilder();
        try (Connection conn = dataSource.getConnection()) {
            sb.append("连接成功，数据库版本: ")
              .append(conn.getMetaData().getDatabaseProductVersion());
        } catch (Throwable e) {
            sb.append("连接失败，完整死因（从外层到内层）：\n");
            Throwable t = e;
            int layer = 1;
            while (t != null) {
                sb.append("第").append(layer).append("层 ")
                  .append(t.getClass().getName())
                  .append(": ").append(t.getMessage()).append("\n");
                t = t.getCause();
                layer++;
            }
        }
        return sb.toString();
    }
}
