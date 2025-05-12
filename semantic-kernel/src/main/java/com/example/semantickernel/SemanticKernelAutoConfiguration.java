package com.example.semantickernel;

import com.azure.ai.openai.OpenAIAsyncClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.KeyCredential;
import com.microsoft.semantickernel.aiservices.openai.chatcompletion.OpenAIChatCompletion;
import com.microsoft.semantickernel.aiservices.openai.textembedding.OpenAITextEmbeddingGenerationService;
import com.microsoft.semantickernel.data.redis.RedisJsonVectorStoreRecordCollectionOptions;
import com.microsoft.semantickernel.data.redis.RedisStorageType;
import com.microsoft.semantickernel.data.redis.RedisVectorStore;
import com.microsoft.semantickernel.data.redis.RedisVectorStoreOptions;
import com.microsoft.semantickernel.data.vectorstorage.VectorStore;
import com.microsoft.semantickernel.data.vectorstorage.VectorStoreRecordCollectionOptions;
import com.microsoft.semantickernel.services.chatcompletion.ChatCompletionService;
import com.microsoft.semantickernel.services.textembedding.TextEmbeddingGenerationService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;

@Configuration
class SemanticKernelAutoConfiguration {

	@ConditionalOnProperty(name = "semantickernel.openai.api-key")
	@ConfigurationProperties("semantickernel.openai")
	@Bean
	OpenAiConnectionProperties openAiConnectionProperties() {
		return new OpenAiConnectionProperties();
	}

	@ConditionalOnProperty(name = "semantickernel.azure-openai.api-key")
	@ConfigurationProperties("semantickernel.azure-openai")
	@Bean
	OpenAiConnectionProperties azureOpenAiConnectionProperties() {
		return new OpenAiConnectionProperties();
	}

	@ConditionalOnProperty(name = "semantickernel.openai.chat-model.model-name")
	@ConfigurationProperties("semantickernel.openai.chat-model")
	@Bean
	ModelProperties openAiChatModelProperties() {
		return new ModelProperties();
	}

	@ConditionalOnProperty(name = "semantickernel.azure-openai.chat-model.deployment-name")
	@ConfigurationProperties("semantickernel.azure-openai.chat-model")
	@Bean
	ModelProperties azureOpenAiChatModelProperties() {
		return new AzureOpenAiModelProperties();
	}

	@ConditionalOnProperty(name = "semantickernel.openai.embedding-model.model-name")
	@ConfigurationProperties("semantickernel.openai.embedding-model")
	@Bean
	ModelProperties openAiEmbeddingModelProperties() {
		return new ModelProperties();
	}

	@ConditionalOnProperty(name = "semantickernel.azure-openai.embedding-model.deployment-name")
	@ConfigurationProperties("semantickernel.azure-openai.embedding-model")
	@Bean
	ModelProperties azureOpenAiEmbeddingModelProperties() {
		return new AzureOpenAiModelProperties();
	}

	@ConditionalOnBean(OpenAiConnectionProperties.class)
	@Bean
	OpenAIAsyncClient openAiClient(OpenAiConnectionProperties properties) {
		return new OpenAIClientBuilder()
				.endpoint(properties.getEndpoint())
				.credential(new KeyCredential(properties.getApiKey()))
				.buildAsyncClient();
	}

	@ConditionalOnBean(OpenAIAsyncClient.class)
	@Bean
	ChatCompletionService openAiChatCompletionService(OpenAIAsyncClient client,
			@Qualifier("openAiChatModelProperties") ModelProperties modelProperties) {
		return OpenAIChatCompletion.builder()
				.withModelId(modelProperties.getModelName())
				.withOpenAIAsyncClient(client)
				.build();
	}

	@ConditionalOnProperty(name = "semantickernel.azure-openai.chat-model.deployment-name", havingValue = "true")
	@ConditionalOnBean(OpenAIAsyncClient.class)
	@Bean
	ChatCompletionService azureOpenAiChatCompletionService(OpenAIAsyncClient client,
			@Qualifier("azureOpenAiChatModelProperties") AzureOpenAiModelProperties modelProperties) {
		return OpenAIChatCompletion.builder()
				.withDeploymentName(modelProperties.getDeploymentName())
				.withModelId(modelProperties.getModelName())
				.withOpenAIAsyncClient(client)
				.build();
	}

	@ConditionalOnProperty(name = "semantickernel.openai.embedding-model.model-name")
	@ConditionalOnBean(OpenAIAsyncClient.class)
	@Bean
	TextEmbeddingGenerationService openAiEmbeddingGenerationService(OpenAIAsyncClient client,
			@Qualifier("openAiEmbeddingModelProperties") ModelProperties modelProperties) {
		return OpenAITextEmbeddingGenerationService.builder()
				.withModelId(modelProperties.getModelName())
				.withOpenAIAsyncClient(client)
				.build();
	}

	@ConditionalOnProperty(name = "semantickernel.azure-openai.embedding-model.deployment-name")
	@ConditionalOnBean(OpenAIAsyncClient.class)
	@Bean
	TextEmbeddingGenerationService azureOpenAiEmbeddingGenerationService(OpenAIAsyncClient client,
			@Qualifier("azureOpenAiEmbeddingModelProperties") AzureOpenAiModelProperties modelProperties) {
		return OpenAITextEmbeddingGenerationService.builder()
				.withDeploymentName(modelProperties.getDeploymentName())
				.withModelId(modelProperties.getModelName())
				.withOpenAIAsyncClient(client)
				.build();
	}

	@ConditionalOnProperty(name = "semantickernel.redis.host")
	@ConfigurationProperties("semantickernel.redis")
	@Bean
	VectorStoreProperties vectorStoreProperties() {
		return new VectorStoreProperties();
	}

	@Bean
	VectorStore vectorStore(VectorStoreProperties properties) {
		var client = new JedisPooled(new HostAndPort(properties.getHost(), properties.getPort()));
		var options = RedisVectorStoreOptions.builder().withStorageType(RedisStorageType.JSON).build();
		return RedisVectorStore.builder().withClient(client).withOptions(options).build();
	}

	@Bean
	VectorStoreRecordCollectionOptions<String, DocumentEmbedding> vectorStoreRecordCollection() {
		return RedisJsonVectorStoreRecordCollectionOptions.<DocumentEmbedding>builder().withRecordClass(DocumentEmbedding.class).build();
	}

	@Bean
	AiModelsDisplayNames aiModelsDisplayNames(ChatCompletionService chatCompletionService) {
		return AiModelsDisplayNames.from(chatCompletionService);
	}
}
