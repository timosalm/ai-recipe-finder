package com.example;

import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RecipeFinderConfiguration {

    // Registers the autoconfigured ChatModel bean for different AI providers under the generic name "chatModel"
    @Bean
    ChatModel chatModel(ChatModel chatModel) {
        return chatModel;
    }


    // Configuration of ETL pipeline orchestrating the flow from raw data sources to a structured vector store
    @Bean
    EmbeddingStoreIngestor embeddingStoreIngestor (EmbeddingStore<TextSegment> embeddingStore, EmbeddingModel embeddingModel) {
        return EmbeddingStoreIngestor.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .documentSplitter(DocumentSplitters.recursive(800, 200))
                .build();
    }

    @ConditionalOnProperty(prefix = "langchain4j.redis", name = "enabled", havingValue = "false")
    @Bean
    EmbeddingStore<TextSegment> simpleEmbeddingStore() {
        return new InMemoryEmbeddingStore<>();
    }
}