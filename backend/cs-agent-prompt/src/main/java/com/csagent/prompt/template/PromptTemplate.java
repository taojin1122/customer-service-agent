package com.csagent.prompt.template;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 提示词模板加载器。
 * <p>
 * 从 classpath:prompts/ 目录加载 .st 模板文件，支持 {{placeholder}} 占位符渲染。
 * 将「提示词」与「代码」分离，是 Prompt 工程的重要实践：修改话术无需改代码。
 */
@Component
public class PromptTemplate {

    private static final String TEMPLATE_DIR = "prompts/";

    /**
     * 渲染模板：模板路径（不含前缀与后缀） + 变量
     */
    public String render(String templateName, Map<String, Object> vars) {
        String raw = load(templateName);
        String result = raw;
        if (vars != null) {
            for (Map.Entry<String, Object> e : vars.entrySet()) {
                result = result.replace("{{" + e.getKey() + "}}", String.valueOf(e.getValue()));
            }
        }
        return result;
    }

    private String load(String templateName) {
        ClassPathResource resource = new ClassPathResource(TEMPLATE_DIR + templateName + ".st");
        try {
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("提示词模板不存在：" + templateName, e);
        }
    }
}
