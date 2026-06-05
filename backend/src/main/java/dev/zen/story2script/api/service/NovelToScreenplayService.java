package dev.zen.story2script.api.service;

import dev.zen.story2script.api.dto.ConvertRequest;
import dev.zen.story2script.api.dto.ConvertResponse;

// 暴露给 API 层的服务边界；实现类不应把 Spring AI 细节泄漏到 Controller。
public interface NovelToScreenplayService {

    // 后续真实实现可以接入 agent 子包服务，但 Controller 只依赖这个方法契约。
    ConvertResponse convert(ConvertRequest request);
}
