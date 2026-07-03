package com.docai.rag;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public List<float[]> embedChunks(List<String> chunks) {
        List<float[]> embeddings = new ArrayList<>();
        if (chunks == null || chunks.isEmpty()) {
            return embeddings;
        }

        // Batch embedding calls: 50 chunks per Gemini request
        int batchSize = 50;
        for (int i = 0; i < chunks.size(); i += batchSize) {
            List<String> batch = chunks.subList(i, Math.min(i + batchSize, chunks.size()));
            EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(batch, null));

            for (org.springframework.ai.embedding.Embedding res : response.getResults()) {
                List<Double> output = res.getOutput();
                float[] vector = new float[output.size()];
                for (int j = 0; j < output.size(); j++) {
                    vector[j] = output.get(j).floatValue();
                }
                embeddings.add(vector);
            }
        }
        return embeddings;
    }

    public float[] embedQuery(String query) {
        List<Double> output = embeddingModel.embed(query);
        float[] vector = new float[output.size()];
        for (int i = 0; i < output.size(); i++) {
            vector[i] = output.get(i).floatValue();
        }
        return vector;
    }
}
