package dev.zen.story2script.api.controller;

import dev.zen.story2script.api.dto.HealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 存活检查端点，供前端、部署探针或本地联调快速确认后端进程可访问。
@RestController
@RequestMapping("/api/health")
class HealthController {

    @GetMapping
    HealthResponse health() {
        // 当前只表达应用进程可响应；不检查模型、数据库或外部服务依赖。
        return new HealthResponse("ok");
    }
}
