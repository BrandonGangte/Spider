package dev.spider.service;

import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class SpiderAssistantFactory {

    private final String baseUrl;
    private final double temperature;
    private final Duration timeout;
    private final int maxMessages;
    private final ChatMemoryStore memoryStore;
    private final SpiderTools spiderTools;
    private final SpiderWebTools spiderWebTools;
    private final ToolActivityRecorder activityRecorder;
    private final ConcurrentMap<String, SpiderAssistant> assistants = new ConcurrentHashMap<>();

    public SpiderAssistantFactory(
            @Value("${spider.ollama.base-url}") String baseUrl,
            @Value("${spider.ollama.temperature}") double temperature,
            @Value("${spider.ollama.timeout}") Duration timeout,
            @Value("${spider.chat.max-messages}") int maxMessages,
            ChatMemoryStore memoryStore,
            SpiderTools spiderTools,
            SpiderWebTools spiderWebTools,
            ToolActivityRecorder activityRecorder) {
        this.baseUrl = baseUrl;
        this.temperature = temperature;
        this.timeout = timeout;
        this.maxMessages = maxMessages;
        this.memoryStore = memoryStore;
        this.spiderTools = spiderTools;
        this.spiderWebTools = spiderWebTools;
        this.activityRecorder = activityRecorder;
    }

    public SpiderAssistant forModel(String modelName) {
        return assistants.computeIfAbsent(modelName, this::createAssistant);
    }

    private SpiderAssistant createAssistant(String modelName) {
        var model = OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .timeout(timeout)
                .build();

        return AiServices.builder(SpiderAssistant.class)
                .chatModel(model)
                .tools(spiderTools, spiderWebTools)
                .beforeToolExecution(activityRecorder::before)
                .afterToolExecution(activityRecorder::after)
                .maxSequentialToolsInvocations(6)
                .maxToolCallingRoundTrips(6)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(maxMessages)
                        .chatMemoryStore(memoryStore)
                        .build())
                .build();
    }
}
