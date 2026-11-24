package dev.spider.api;

import dev.langchain4j.data.message.Content;
import dev.spider.service.ChatAttachmentService;
import dev.spider.service.SpiderAssistantFactory;
import dev.spider.service.ToolActivityRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final SpiderAssistantFactory assistantFactory;
    private final SpiderStatusService statusService;
    private final ToolActivityRecorder activityRecorder;
    private final ChatAttachmentService attachmentService;

    public ChatController(SpiderAssistantFactory assistantFactory, SpiderStatusService statusService,
                          ToolActivityRecorder activityRecorder, ChatAttachmentService attachmentService) {
        this.assistantFactory = assistantFactory;
        this.statusService = statusService;
        this.activityRecorder = activityRecorder;
        this.attachmentService = attachmentService;
    }

    @GetMapping("/status")
    public SpiderStatusService.Status status(@RequestParam(required = false) String model) {
        return statusService.getStatus(model);
    }

    @GetMapping("/models")
    public SpiderStatusService.ModelCatalog models() {
        return statusService.getModelCatalog();
    }

    @GetMapping("/{conversationId}/activity")
    public List<ToolActivityRecorder.ToolActivity> activity(@PathVariable String conversationId) {
        return activityRecorder.getActivities(conversationId);
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            return ResponseEntity.badRequest().body(new ChatResponse(null, null,
                    "Please enter a message before sending."));
        }

        String modelName = resolveModel(request.model());
        if (!statusService.isModelAvailable(modelName)) {
            return ResponseEntity.badRequest().body(new ChatResponse(null, null,
                    "The selected Ollama model is not installed or Ollama is unavailable. Refresh the model list and try again."));
        }

        String conversationId = request.conversationId() == null || request.conversationId().isBlank()
                ? UUID.randomUUID().toString()
                : request.conversationId().trim();

        long startedAt = System.nanoTime();
        log.info("Chat started conversationId={} model={}", conversationId, modelName);
        try {
            String answer = assistantFactory.forModel(modelName).chat(conversationId, request.message().trim());
            log.info("Chat completed conversationId={} model={} durationMs={}", conversationId, modelName, elapsedMillis(startedAt));
            return ResponseEntity.ok(new ChatResponse(conversationId, answer, null, modelName));
        } catch (RuntimeException exception) {
            log.warn("Chat failed conversationId={} model={} durationMs={}", conversationId, modelName, elapsedMillis(startedAt), exception);
            return ResponseEntity.status(502).body(new ChatResponse(conversationId, null,
                    "Spider could not get a response from Ollama. Check that Ollama is running and the selected model is available.", modelName));
        }
    }

    @PostMapping(value = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ChatResponse> chatWithAttachments(
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false) String message,
            @RequestParam(required = false) String model,
            @RequestPart("files") List<MultipartFile> files) {
        if ((message == null || message.isBlank()) && (files == null || files.isEmpty())) {
            return ResponseEntity.badRequest().body(new ChatResponse(null, null,
                    "Enter a message or attach at least one file."));
        }
        String id = conversationId == null || conversationId.isBlank()
                ? UUID.randomUUID().toString()
                : conversationId.trim();
        String prompt = message == null || message.isBlank()
                ? "Please review the attached files and help me with them."
                : message.trim();
        String modelName = resolveModel(model);
        if (!statusService.isModelAvailable(modelName)) {
            return ResponseEntity.badRequest().body(new ChatResponse(id, null,
                    "The selected Ollama model is not installed or Ollama is unavailable. Refresh the model list and try again.", modelName));
        }
        List<Content> contents;
        try {
            contents = attachmentService.toContents(files);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(new ChatResponse(id, null, exception.getMessage()));
        }

        long startedAt = System.nanoTime();
        log.info("Chat with attachments started conversationId={} attachmentCount={}", id, files.size());
        try {
            String answer = assistantFactory.forModel(modelName).chatWithAttachments(id, prompt, contents);
            log.info("Chat with attachments completed conversationId={} model={} durationMs={}", id, modelName, elapsedMillis(startedAt));
            return ResponseEntity.ok(new ChatResponse(id, answer, null, modelName));
        } catch (RuntimeException exception) {
            log.warn("Chat with attachments failed conversationId={} model={} durationMs={}", id, modelName, elapsedMillis(startedAt), exception);
            return ResponseEntity.status(502).body(new ChatResponse(id, null,
                    "Spider could not process the attachments with Ollama. Check that Ollama is running and the selected model supports the attached content.", modelName));
        }
    }

    private String resolveModel(String requestedModel) {
        return requestedModel == null || requestedModel.isBlank()
                ? statusService.getModelCatalog().configuredModel()
                : requestedModel.trim();
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    public record ChatRequest(String conversationId, String message, String model) {
    }

    public record ChatResponse(String conversationId, String answer, String error, String model) {
        public ChatResponse(String conversationId, String answer, String error) {
            this(conversationId, answer, error, null);
        }
    }
}
