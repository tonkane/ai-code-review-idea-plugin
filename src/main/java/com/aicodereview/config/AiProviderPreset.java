package com.aicodereview.config;

/**
 * Common OpenAI-compatible providers. Selecting a preset only fills in the
 * endpoint and the model name; it never touches the stored API key.
 */
public enum AiProviderPreset {
    DEEPSEEK("DeepSeek", "https://api.deepseek.com/chat/completions", "deepseek-chat"),
    OPENAI("OpenAI", "https://api.openai.com/v1/chat/completions", "gpt-4o-mini"),
    QWEN("通义千问（DashScope 兼容模式）",
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", "qwen-plus"),
    MOONSHOT("Moonshot Kimi", "https://api.moonshot.cn/v1/chat/completions", "moonshot-v1-8k"),
    ZHIPU("智谱 GLM", "https://open.bigmodel.cn/api/paas/v4/chat/completions", "glm-4-flash"),
    SILICONFLOW("SiliconFlow", "https://api.siliconflow.cn/v1/chat/completions",
            "deepseek-ai/DeepSeek-V3"),
    OLLAMA("Ollama（本地）", "http://localhost:11434/v1/chat/completions", "qwen2.5-coder:7b"),
    CUSTOM("自定义 / 其他兼容接口", "", "");

    private final String displayName;
    private final String endpoint;
    private final String model;

    AiProviderPreset(String displayName, String endpoint, String model) {
        this.displayName = displayName;
        this.endpoint = endpoint;
        this.model = model;
    }

    public String displayName() {
        return displayName;
    }

    public String endpoint() {
        return endpoint;
    }

    public String model() {
        return model;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
