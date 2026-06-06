package dev.zen.story2script.config;

import java.lang.reflect.Proxy;

import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.ClassUtils;

/**
 * Dev-only fallback wiring for tool beans that depend on an LLM client.
 *
 * <p>The default {@code dev} profile starts without an OpenAI API key or chat model. This configuration lets the
 * Spring context load in that state while still failing clearly if a tool actually attempts an LLM call.
 */
@Configuration(proxyBeanMethods = false)
public class DevFallbackToolLlmClientConfiguration {

    private static final String TOOL_LLM_CLIENT_CLASS_NAME = "dev.zen.story2script.tools.ToolLlmClient";

    /**
     * Registers the fallback by type name instead of importing the tool interface directly.
     *
     * <p>That keeps this config package from requiring the optional {@code tools} package at compile time, while still
     * supporting dev startup when tool beans exist.
     */
    @Bean
    @Profile("dev")
    static BeanDefinitionRegistryPostProcessor fallbackToolLlmClientBeanDefinition() {
        return new BeanDefinitionRegistryPostProcessor() {
            @Override
            public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
                Class<?> toolLlmClientType = resolveToolLlmClientType();
                if (toolLlmClientType == null || registry.containsBeanDefinition("fallbackToolLlmClient")) {
                    return;
                }

                RootBeanDefinition beanDefinition = new RootBeanDefinition(toolLlmClientType);
                beanDefinition.setInstanceSupplier(() -> fallbackToolLlmClient(toolLlmClientType));
                registry.registerBeanDefinition("fallbackToolLlmClient", beanDefinition);
            }

            private Class<?> resolveToolLlmClientType() {
                try {
                    return ClassUtils.forName(
                            TOOL_LLM_CLIENT_CLASS_NAME,
                            DevFallbackToolLlmClientConfiguration.class.getClassLoader()
                    );
                }
                catch (ClassNotFoundException ex) {
                    return null;
                }
            }
        };
    }

    /**
     * Creates a no-model proxy that fails only when a tool attempts an LLM call.
     */
    private static Object fallbackToolLlmClient(Class<?> toolLlmClientType) {
        return Proxy.newProxyInstance(
                toolLlmClientType.getClassLoader(),
                new Class<?>[] { toolLlmClientType },
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "equals" -> proxy == args[0];
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "toString" -> "fallbackToolLlmClient";
                            default -> null;
                        };
                    }
                    throw new IllegalStateException(
                            "No Spring AI ChatModel is configured. Configure a model provider before invoking LLM tools."
                    );
                }
        );
    }
}
