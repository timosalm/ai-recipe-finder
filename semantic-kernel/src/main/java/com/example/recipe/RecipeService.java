package com.example.recipe;

import com.microsoft.semantickernel.Kernel;
import com.microsoft.semantickernel.orchestration.InvocationContext;
import com.microsoft.semantickernel.orchestration.PromptExecutionSettings;
import com.microsoft.semantickernel.orchestration.ToolCallBehavior;
import com.microsoft.semantickernel.semanticfunctions.KernelFunctionArguments;
import com.microsoft.semantickernel.semanticfunctions.annotations.DefineKernelFunction;
import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class RecipeService {

    private static final Logger log = LoggerFactory.getLogger(RecipeService.class);
	private final Kernel defaultKernel;
	private final Kernel kernelWithToolCalling;

	@Value("classpath:/prompts/recipe-for-ingredients")
	private Resource recipeForIngredientsPromptResource;

	@Value("classpath:/prompts/recipe-for-available-ingredients")
	private Resource recipeForAvailableIngredientsPromptResource;

	@Value("classpath:/prompts/prefer-own-recipe")
	private Resource preferOwnRecipePromptResource;

	@Value("classpath:/prompts/fix-json-response")
	private Resource fixJsonResponsePromptResource;

	@Value("${app.available-ingredients-in-fridge}")
    private List<String> availableIngredientsInFridge;

    RecipeService(Kernel defaultKernel,
				  @Lazy @Qualifier("kernelWithToolCalling") Kernel kernelWithToolCalling) {
		this.defaultKernel = defaultKernel;
		this.kernelWithToolCalling = kernelWithToolCalling;
	}

    void addRecipeDocumentForRag(Resource pdfResource) throws IOException {
        log.info("Add recipe document {} for rag", pdfResource.getFilename());
		throw new NotImplementedException();

/*
        var documentParser = new ApachePdfBoxDocumentParser();
        var document = documentParser.parse(pdfResource.getInputStream());
        embeddingStoreIngestor.ingest(document);
        */

    }

    Recipe fetchRecipeFor(List<String> ingredients, boolean preferAvailableIngredients, boolean preferOwnRecipes) throws IOException {
        Recipe recipe;
        var ingredientsStr = String.join(",", ingredients);
        if (!preferAvailableIngredients && !preferOwnRecipes) {
			recipe = fetchRecipeFor(ingredientsStr);
		} else if (preferAvailableIngredients && !preferOwnRecipes) {
			recipe = fetchRecipeWithToolCallingFor(ingredientsStr);
        } else if (!preferAvailableIngredients && preferOwnRecipes) {
            recipe = fetchRecipeWithRagFor(ingredientsStr);
        } else {
            recipe = fetchRecipeWithRagAndToolCallingFor(ingredientsStr);
        }
		return recipe;
    }

	private Recipe fetchRecipeFor(String ingredientsStr) throws IOException {
		var systemPrompt = fixJsonResponsePromptResource.getContentAsString(StandardCharsets.UTF_8);
		var userPromptTemplate = recipeForIngredientsPromptResource.getContentAsString(StandardCharsets.UTF_8);
		var combinedPromptTemplate = String.join(systemPrompt, "\n\n", userPromptTemplate);
		var arguments = KernelFunctionArguments.builder().withVariable("ingredients", ingredientsStr).build();

		var promptExecutionSettings = PromptExecutionSettings.builder()
				.withJsonSchemaResponseFormat(Recipe.class)
				.withMaxTokens(800)
				.build();

		var invocationContext = InvocationContext.builder()
				.withPromptExecutionSettings(promptExecutionSettings)
				.build();

		return 	defaultKernel.invokePromptAsync(combinedPromptTemplate, arguments, invocationContext)
				.withResultTypeAutoConversion(Recipe.class)
				.block().getResult();
	}

	private Recipe fetchRecipeWithToolCallingFor(String ingredientsStr) throws IOException {
		var systemPrompt = fixJsonResponsePromptResource.getContentAsString(StandardCharsets.UTF_8);
		var userPromptTemplate = recipeForAvailableIngredientsPromptResource.getContentAsString(StandardCharsets.UTF_8);
		var combinedPromptTemplate = String.join(systemPrompt, "\n\n", userPromptTemplate);
		var arguments = KernelFunctionArguments.builder().withVariable("ingredients", ingredientsStr).build();

		var promptExecutionSettings = PromptExecutionSettings.builder()
				.withJsonSchemaResponseFormat(Recipe.class)
				.withMaxTokens(800)
				.build();

		var invocationContext = InvocationContext.builder()
				.withPromptExecutionSettings(promptExecutionSettings)
				.withToolCallBehavior(ToolCallBehavior.allowAllKernelFunctions(true))
				.build();

		return 	kernelWithToolCalling.invokePromptAsync(combinedPromptTemplate, arguments, invocationContext)
				.withResultTypeAutoConversion(Recipe.class)
				.block().getResult();
	}

	@DefineKernelFunction(name = "fetch_ingredients_available_at_home", description = "Fetches ingredients that are available at home",
            returnType = "java.util.List")
    public List<String> fetchIngredientsAvailableAtHome() {
        log.info("Fetching ingredients available at home function called by LLM");
        return availableIngredientsInFridge;
    }

	private Recipe fetchRecipeWithRagFor(String ingredientsStr) {
		https://github.com/Azure-Samples/azure-search-openai-demo-java
		throw new NotImplementedException();
	}

	private Recipe fetchRecipeWithRagAndToolCallingFor(String ingredientsStr) {
		throw new NotImplementedException();
	}
}