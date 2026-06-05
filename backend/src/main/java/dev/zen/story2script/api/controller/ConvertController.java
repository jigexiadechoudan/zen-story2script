package dev.zen.story2script.api.controller;

import dev.zen.story2script.api.dto.ConvertRequest;
import dev.zen.story2script.api.dto.ConvertResponse;
import dev.zen.story2script.api.service.NovelToScreenplayService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 轻量 HTTP 边界：校验请求结构，并把转换工作委托给 API 服务接口。
@RestController
@RequestMapping("/api/convert")
class ConvertController {

    private final NovelToScreenplayService service;

    ConvertController(NovelToScreenplayService service) {
        this.service = service;
    }

    @PostMapping
    ConvertResponse convert(@Valid @RequestBody ConvertRequest request) {
        // Controller 不直接拼装剧本，也不调用 Spring AI；真实实现应隐藏在服务接口后面。
        return service.convert(request);
    }
}
