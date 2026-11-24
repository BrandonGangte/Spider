package dev.spider.service;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.ToolExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ToolActivityRecorder {

    private static final Logger log = LoggerFactory.getLogger(ToolActivityRecorder.class);
    private static final int MAX_ACTIVITIES_PER_CONVERSATION = 100;
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<ToolActivity>> activities = new ConcurrentHashMap<>();

    public void before(BeforeToolExecution execution) {
        String conversationId = conversationId(execution.invocationContext().chatMemoryId());
        ToolExecutionRequest request = execution.request();
        String activityId = request.id() == null || request.id().isBlank()
                ? UUID.randomUUID().toString()
                : request.id();
        CopyOnWriteArrayList<ToolActivity> conversationActivities = activities.computeIfAbsent(
                conversationId, ignored -> new CopyOnWriteArrayList<>());
        long startedAt = System.currentTimeMillis();
        conversationActivities.add(new ToolActivity(activityId, request.name(), "RUNNING", startedAt, null));
        log.info("Agent tool started conversationId={} tool={}", conversationId, request.name());
        trim(conversationActivities);
    }

    public void after(ToolExecution execution) {
        String conversationId = conversationId(execution.invocationContext().chatMemoryId());
        String requestId = execution.request().id();
        CopyOnWriteArrayList<ToolActivity> conversationActivities = activities.get(conversationId);
        if (conversationActivities == null) {
            return;
        }

        for (int index = conversationActivities.size() - 1; index >= 0; index--) {
            ToolActivity activity = conversationActivities.get(index);
            boolean sameCall = requestId != null && requestId.equals(activity.id());
            boolean sameUnnamedCall = requestId == null && activity.status().equals("RUNNING")
                    && activity.toolName().equals(execution.request().name());
            if (sameCall || sameUnnamedCall) {
                conversationActivities.set(index, new ToolActivity(
                        activity.id(), activity.toolName(), execution.hasFailed() ? "FAILED" : "COMPLETED",
                        activity.startedAt(), System.currentTimeMillis()));
                long durationMs = Math.max(0, System.currentTimeMillis() - activity.startedAt());
                log.info("Agent tool {} conversationId={} tool={} durationMs={}",
                        execution.hasFailed() ? "failed" : "completed", conversationId,
                        activity.toolName(), durationMs);
                return;
            }
        }
    }

    public List<ToolActivity> getActivities(String conversationId) {
        List<ToolActivity> conversationActivities = activities.get(conversationId);
        return conversationActivities == null ? List.of() : List.copyOf(conversationActivities);
    }

    private static void trim(CopyOnWriteArrayList<ToolActivity> conversationActivities) {
        while (conversationActivities.size() > MAX_ACTIVITIES_PER_CONVERSATION) {
            conversationActivities.remove(0);
        }
    }

    private static String conversationId(Object memoryId) {
        return memoryId == null ? "unknown" : memoryId.toString();
    }

    public record ToolActivity(String id, String toolName, String status, long startedAt, Long finishedAt) {
    }
}
