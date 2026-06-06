/**
 * 小说转剧本 Agent 可调用的工具包。
 *
 * <p>审查时可以按下面三类看：</p>
 * <ul>
 *     <li>确定性预处理：{@link dev.zen.story2script.tools.ChapterParseTool}</li>
 *     <li>LLM 调用抽象和适配：{@link dev.zen.story2script.tools.ToolLlmClient}</li>
 *     <li>LLM 转换工具：故事分析、分场规划、YAML 草稿生成、YAML 修复</li>
 * </ul>
 *
 * <p>每个工具只做一个边界清晰的操作。这里不实现 Controller、Agent 主循环、
 * 重试策略，也不修改 YAML schema 校验规则。</p>
 */
@org.springframework.lang.NonNullApi
package dev.zen.story2script.tools;
