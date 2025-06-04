package com.example;

import com.example.recipe.RecipeService;
import com.microsoft.semantickernel.Kernel;
import com.microsoft.semantickernel.plugin.KernelPluginFactory;
import com.microsoft.semantickernel.services.chatcompletion.ChatCompletionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class RecipeFinderConfiguration {

    public final static String  RECIPE_SERVICE_TOOLS_KERNEL_PLUGIN_NAME = "RecipeService";

    @Primary
    @Bean
    public Kernel defaultKernel(ChatCompletionService chatCompletionService) {
        return Kernel.builder()
                .withAIService(ChatCompletionService.class, chatCompletionService)
                .build();
    }

    @Bean
    public Kernel kernelWithToolCalling(ChatCompletionService chatCompletionService, RecipeService recipeService) {
        return Kernel.builder()
                .withAIService(ChatCompletionService.class, chatCompletionService)
                // Provides tools (methods annotated with @Tool) from the RecipeService instance
                .withPlugin(KernelPluginFactory.createFromObject(recipeService, RECIPE_SERVICE_TOOLS_KERNEL_PLUGIN_NAME))
                .build();
    }


}