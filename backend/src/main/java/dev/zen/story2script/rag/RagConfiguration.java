package dev.zen.story2script.rag;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RagProperties.class)
class RagConfiguration {
}
