package dev.zen.story2script.config;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ClassUtils;

/**
 * Registers Spring AI tool callbacks from project tool beans.
 *
 * <p>Future tool classes only need to live under {@code dev.zen.story2script.tools}, be registered as Spring beans,
 * and annotate callable methods with Spring AI {@link Tool}. This keeps agent orchestration code from needing to know
 * each tool class individually.
 */
@Configuration(proxyBeanMethods = false)
public class AgentToolConfiguration {

    private static final String TOOL_PACKAGE_NAME = "dev.zen.story2script.tools";

    /**
     * Exposes a single provider for agent tools discovered from Spring beans.
     */
    @ConditionalOnMissingBean(name = "agentToolCallbackProvider")
    ToolCallbackProvider agentToolCallbackProvider(ListableBeanFactory beanFactory) {
        Object[] toolBeans = Arrays.stream(beanFactory.getBeanNamesForType(Object.class))
                .filter(beanName -> isAgentToolBean(beanFactory.getType(beanName)))
                .map(beanFactory::getBean)
                .toArray();

        if (toolBeans.length == 0) {
            return ToolCallbackProvider.from();
        }

        return MethodToolCallbackProvider.builder()
                .toolObjects(toolBeans)
                .build();
    }

    /**
     * Limits auto-registration to the project tool package and classes that actually expose {@link Tool} methods.
     */
    private boolean isAgentToolBean(Class<?> beanType) {
        if (beanType == null) {
            return false;
        }
        Class<?> userClass = ClassUtils.getUserClass(beanType);
        Package beanPackage = userClass.getPackage();
        if (beanPackage == null || !beanPackage.getName().startsWith(TOOL_PACKAGE_NAME)) {
            return false;
        }
        return hasToolMethod(userClass);
    }

    /**
     * Spring AI turns public methods annotated with {@link Tool} into callable tool callbacks.
     */
    private boolean hasToolMethod(Class<?> userClass) {
        List<Method> methods = Arrays.stream(userClass.getMethods()).toList();
        return methods.stream().anyMatch(method -> method.isAnnotationPresent(Tool.class));
    }
}
