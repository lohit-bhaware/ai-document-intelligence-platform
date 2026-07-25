package com.docai.rag;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Calls Gemini's OpenAI-compatible embeddings endpoint directly via RestClient,
 * instead of going through Spring AI's OpenAiEmbeddingModel.
 *
 * Why: Spring AI's OpenAiEmbeddingModel unconditionally reads token usage stats
 * off the response and throws a NullPointerException, because Gemini's embeddings
 * endpoint (unlike its chat completions endpoint) doesn't return a "usage" object.
 * This is a known, still-open Spring AI issue (spring-projects/spring-ai#2485).
 * Calling the endpoint directly sidesteps it entirely.
 */
@Service
@Slf4j
public class EmbeddingService {

    private final RestClient restClient;
    private final String model;

    public EmbeddingService(
            @Value("${spring.ai.openai.base-url}") String baseUrl,
            @Value("${spring.ai.openai.api-key}") String apiKey,
            @Value("${spring.ai.openai.embedding.options.model}") String model) {
        this.model = model;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    public List<float[]> embedChunks(List<String> chunks) {
        List<float[]> embeddings = new ArrayList<>();
        if (chunks == null || chunks.isEmpty()) {
            return embeddings;
        }

        // Batch embedding calls: 50 chunks per Gemini request
        int batchSize = 50;
        for (int i = 0; i < chunks.size(); i += batchSize) {
            List<String> batch = chunks.subList(i, Math.min(i + batchSize, chunks.size()));
            embeddings.addAll(callEmbeddingsEndpoint(batch));
        }
        return embeddings;
    }

    public float[] embedQuery(String query) {
        List<float[]> result = callEmbeddingsEndpoint(List.of(query));
        return result.isEmpty() ? new float[0] : result.get(0);
    }

    private List<float[]> callEmbeddingsEndpoint(List<String> inputs) {
        EmbeddingApiResponse response = restClient.post()
                .uri("/embeddings")
                .body(Map.of("model", model, "input", inputs))
                .retrieve()
                .body(EmbeddingApiResponse.class);

        List<float[]> result = new ArrayList<>();
        if (response == null || response.data() == null) {
            log.warn("Empty embedding response for batch of {} inputs", inputs.size());
            return result;
        }

        for (EmbeddingData item : response.data()) {
            List<Double> vec = item.embedding();
            float[] arr = new float[vec.size()];
            for (int j = 0; j < vec.size(); j++) {
                arr[j] = vec.get(j).floatValue();
            }
            result.add(arr);
        }
        return result;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmbeddingApiResponse(List<EmbeddingData> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmbeddingData(List<Double> embedding, Integer index) {
    }
}