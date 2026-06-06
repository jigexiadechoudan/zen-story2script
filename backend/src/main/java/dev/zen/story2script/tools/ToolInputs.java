package dev.zen.story2script.tools;

import java.util.Objects;

/**
 * 工具输入校验辅助类。
 *
 * <p>把公共校验收敛到这里，可以让每个工具的公开方法更容易审查：
 * 工具类只展示主流程，错误文案和空值处理保持一致。</p>
 */
final class ToolInputs {

    private ToolInputs() {
    }

    static <T> T requireInput(T input) {
        return Objects.requireNonNull(input, "input must not be null");
    }

    static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
