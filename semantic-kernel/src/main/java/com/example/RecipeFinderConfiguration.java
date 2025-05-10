package com.example;

import com.example.recipe.RecipeService;
import com.microsoft.semantickernel.Kernel;
import com.microsoft.semantickernel.plugin.KernelPluginFactory;
import com.microsoft.semantickernel.services.chatcompletion.ChatCompletionService;
import com.microsoft.semantickernel.services.textcompletion.TextGenerationService;
import com.microsoft.semantickernel.services.textembedding.TextEmbeddingGenerationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
class RecipeFinderConfiguration {

    @Primary
    @Bean
    public Kernel defaultKernel(ChatCompletionService chatCompletionService,
                TextEmbeddingGenerationService textEmbeddingGenerationService) {
        return Kernel.builder()
                .withAIService(ChatCompletionService.class, chatCompletionService)
                //.withAIService(TextEmbeddingGenerationService.class, textEmbeddingGenerationService)
                .build();
    }

    @Bean
    public Kernel kernelWithToolCalling(ChatCompletionService chatCompletionService, RecipeService recipeService) {
        return Kernel.builder()
                .withAIService(ChatCompletionService.class, chatCompletionService)
                .withPlugin(KernelPluginFactory.createFromObject(recipeService, "RecipeService"))
                .build();
    }


}