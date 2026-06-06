package dev.zen.story2script.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 后端 API 的全局 CORS 配置。
 *
 * <p>前端开发服务运行在 5173 端口，浏览器跨端口访问后端 8080 时会先发送 OPTIONS 预检。
 * 这里显式允许本地 dev origin 和常用请求头，避免预检被 Spring MVC 拒绝。
 */
@Configuration(proxyBeanMethods = false)
public class WebCorsConfiguration implements WebMvcConfigurer {

    private static final String[] DEV_ORIGINS = {
            "http://localhost:5173",
            "http://127.0.0.1:5173"
    };

    private static final String[] API_METHODS = {
            "GET",
            "POST",
            "OPTIONS"
    };

    private static final String[] API_HEADERS = {
            "Content-Type",
            "Authorization"
    };

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 只开放 API 路径，避免把静态资源或未来非 API 路径一并暴露给跨域访问。
        registry.addMapping("/api/**")
                .allowedOrigins(DEV_ORIGINS)
                .allowedMethods(API_METHODS)
                .allowedHeaders(API_HEADERS)
                .maxAge(3600);
    }
}
