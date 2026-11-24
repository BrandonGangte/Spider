package dev.spider.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashSet;

@Service
public class SpiderStatusService {

    private final String baseUrl;
    private final String configuredModel;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    public SpiderStatusService(
            @Value("${spider.ollama.base-url}") String baseUrl,
            @Value("${spider.ollama.model}") String configuredModel,
            ObjectMapper objectMapper) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.configuredModel = configuredModel;
        this.objectMapper = objectMapper;
    }

    public Status getStatus() {
        return getStatus(configuredModel);
    }

    public Status getStatus(String requestedModel) {
        String modelName = requestedModel == null || requestedModel.isBlank() ? configuredModel : requestedModel.trim();
        ModelFetchResult result = fetchModels();
        if (!result.reachable()) {
            return new Status(false, false, modelName);
        }
        return new Status(true, result.models().contains(modelName), modelName);
    }

    public ModelCatalog getModelCatalog() {
        ModelFetchResult result = fetchModels();
        return new ModelCatalog(result.reachable(), configuredModel, result.models());
    }

    public boolean isModelAvailable(String modelName) {
        return getStatus(modelName).modelAvailable();
    }

    private ModelFetchResult fetchModels() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new ModelFetchResult(false, List.of());
            }

            OllamaTags tags = objectMapper.readValue(response.body(), OllamaTags.class);
            LinkedHashSet<String> names = new LinkedHashSet<>();
            if (tags.models() != null) {
                for (OllamaModel model : tags.models()) {
                    if (model.name() != null && !model.name().isBlank()) names.add(model.name());
                    else if (model.model() != null && !model.model().isBlank()) names.add(model.model());
                }
            }
            List<String> models = new ArrayList<>(names);
            models.sort(String.CASE_INSENSITIVE_ORDER);
            return new ModelFetchResult(true, models);
        } catch (Exception exception) {
            return new ModelFetchResult(false, List.of());
        }
    }

    private record ModelFetchResult(boolean reachable, List<String> models) {
    }

    public record Status(boolean ollamaReachable, boolean modelAvailable, String model) {
    }

    public record ModelCatalog(boolean ollamaReachable, String configuredModel, List<String> models) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OllamaTags(List<OllamaModel> models) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OllamaModel(String name, String model) {
    }
}
