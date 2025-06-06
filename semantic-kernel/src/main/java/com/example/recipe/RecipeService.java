package com.example.recipe;

import com.example.semantickernel.DocumentEmbedding;
import com.microsoft.semantickernel.Kernel;
import com.microsoft.semantickernel.data.vectorsearch.VectorSearchResults;
import com.microsoft.semantickernel.data.vectorstorage.VectorStore;
import com.microsoft.semantickernel.data.vectorstorage.VectorStoreRecordCollectionOptions;
import com.microsoft.semantickernel.data.vectorstorage.options.VectorSearchOptions;
import com.microsoft.semantickernel.orchestration.InvocationContext;
import com.microsoft.semantickernel.orchestration.PromptExecutionSettings;
import com.microsoft.semantickernel.orchestration.ToolCallBehavior;
import com.microsoft.semantickernel.semanticfunctions.KernelFunctionArguments;
import com.microsoft.semantickernel.semanticfunctions.PromptTemplateConfig;
import com.microsoft.semantickernel.semanticfunctions.PromptTemplateFactory;
import com.microsoft.semantickernel.semanticfunctions.annotations.DefineKernelFunction;
import com.microsoft.semantickernel.services.textembedding.TextEmbeddingGenerationService;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.text.PDFTextStripper;
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
import java.util.Objects;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.example.RecipeFinderConfiguration.RECIPE_SERVICE_TOOLS_KERNEL_PLUGIN_NAME;

@Service
public class RecipeService {

    private static final Logger log = LoggerFactory.getLogger(RecipeService.class);
	private static final String VECTORSTORE_COLLECTION_NAME = "recipes";
	private static final String TOOL_NAME_RECIPE_DOCUMENTS = "retrieve_documents";

	private final Kernel defaultKernel;
	private final Kernel kernelWithToolCalling;
	private final VectorStore vectorStore;
	private final VectorStoreRecordCollectionOptions collectionOptions;
	private final TextEmbeddingGenerationService embeddingGenerationService;

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

    RecipeService(Kernel defaultKernel, @Lazy @Qualifier("kernelWithToolCalling") Kernel kernelWithToolCalling,
				  VectorStore vectorStore, VectorStoreRecordCollectionOptions<String, DocumentEmbedding> collectionOptions,
				  TextEmbeddingGenerationService embeddingGenerationService) {
		this.defaultKernel = defaultKernel;
		this.kernelWithToolCalling = kernelWithToolCalling;
		this.vectorStore = vectorStore;
		this.collectionOptions = collectionOptions;
		this.embeddingGenerationService = embeddingGenerationService;
	}

	// ETL pipeline orchestrating the flow from raw data sources to a structured vector store
	void addRecipeDocumentForRag(Resource pdfResource) throws IOException {
        log.info("Add recipe document {} for rag", pdfResource.getFilename());

		// Extract: Parses PDF documents
		var pdfParser = new PDFParser(new RandomAccessReadBuffer(pdfResource.getInputStream()));
		var document = pdfParser.parse();

		// Transform: Splits text into chunks
		var documents = new Splitter().split(document);
		var textStripper = new PDFTextStripper();
		var documentsContent = documents.stream().map(d -> {
			try {
				return textStripper.getText(d);
			} catch (IOException e) {
				return null;
			}
		}).filter((Objects::nonNull)).toList();

		// Loads data into vector database
		var embeddings = embeddingGenerationService.generateEmbeddingsAsync(documentsContent).block();
		var documentsEmbeddings = IntStream.range(0, documentsContent.size())
				.mapToObj(i -> new DocumentEmbedding(UUID.randomUUID().toString(), embeddings.get(i).getVector(), documentsContent.get(i)))
				.toList();

		var collection = vectorStore.<String, DocumentEmbedding>getCollection(VECTORSTORE_COLLECTION_NAME, collectionOptions);
		collection.createCollectionIfNotExistsAsync().block();
		collection.upsertBatchAsync(documentsEmbeddings, null).block();
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
				// Structured Output configuration
				.withJsonSchemaResponseFormat(Recipe.class)
				.withMaxTokens(800)
				.build();

		var invocationContext = InvocationContext.builder()
				.withPromptExecutionSettings(promptExecutionSettings)
				.build();

		return 	defaultKernel.invokePromptAsync(combinedPromptTemplate, arguments, invocationContext)
				// Structured Output configuration
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

		var fetchIngredientsAvailableAtHomeTool = kernelWithToolCalling.getFunction(RECIPE_SERVICE_TOOLS_KERNEL_PLUGIN_NAME, "fetch_ingredients_available_at_home");
		var invocationContext = InvocationContext.builder()
				.withPromptExecutionSettings(promptExecutionSettings)
				// Provides tool configured via a Kernel Plugin in RecipeFinderConfiguration
				.withToolCallBehavior(ToolCallBehavior.requireKernelFunction(fetchIngredientsAvailableAtHomeTool))
				.build();

		return 	kernelWithToolCalling.invokePromptAsync(combinedPromptTemplate, arguments, invocationContext)
				.withResultTypeAutoConversion(Recipe.class)
				.block().getResult();
	}

	// Defines a tool
	@DefineKernelFunction(name = "fetch_ingredients_available_at_home", description = "Fetches ingredients that are available at home",
            returnType = "java.util.List")
    public List<String> fetchIngredientsAvailableAtHome() {
        log.info("Fetching ingredients available at home function called by LLM");
        return availableIngredientsInFridge;
    }

	private Recipe fetchRecipeWithRagFor(String ingredientsStr) throws IOException {
		var systemPrompt = fixJsonResponsePromptResource.getContentAsString(StandardCharsets.UTF_8);
		var ragSystemPrompt = preferOwnRecipePromptResource.getContentAsString(StandardCharsets.UTF_8);
		var userPromptTemplate = recipeForIngredientsPromptResource.getContentAsString(StandardCharsets.UTF_8);
		var arguments = KernelFunctionArguments.builder().withVariable("ingredients", ingredientsStr).build();

		// Retrieve via a kernel function configured via a Kernel Plugin in RecipeFinderConfiguration
		var retrieveDocumentsTool = kernelWithToolCalling.getFunction(RECIPE_SERVICE_TOOLS_KERNEL_PLUGIN_NAME, TOOL_NAME_RECIPE_DOCUMENTS);
		var functionArguments = KernelFunctionArguments.builder().withVariable("userPromptTemplate", userPromptTemplate).withVariable("ingredientsStr", ingredientsStr).build();
		List<String> documents = retrieveDocumentsTool.invokeAsync(kernelWithToolCalling)
				.withArguments(functionArguments)
				.withResultType(List.class).block().getResult();

		// Augment
		var combinedPromptTemplate = String.join("\n\n",
				Stream.concat(documents.stream(),
						List.of(systemPrompt, userPromptTemplate, ragSystemPrompt).stream()).toList());

		var promptExecutionSettings = PromptExecutionSettings.builder()
				.withJsonSchemaResponseFormat(Recipe.class)
				.withMaxTokens(800)
				.build();

		var invocationContext = InvocationContext.builder()
				.withPromptExecutionSettings(promptExecutionSettings)
				.build();

		return 	kernelWithToolCalling.invokePromptAsync(combinedPromptTemplate, arguments, invocationContext)
				.withResultTypeAutoConversion(Recipe.class)
				.block().getResult();
	}

	// Defines a kernel function for the RAG retrieval
	@DefineKernelFunction(name = TOOL_NAME_RECIPE_DOCUMENTS, description = "Retrieves related documents with recipes for a user prompt from the vector store",
			returnType = "java.util.List")
	public List<String> retrieveDocuments(String userPromptTemplate, String ingredientsStr) {
		log.info("Retrieve documents from vector store");
		var arguments = KernelFunctionArguments.builder().withVariable("ingredients", ingredientsStr).build();
		var userPrompt = PromptTemplateFactory.build(
				PromptTemplateConfig.builder().withTemplate(userPromptTemplate).build()
		).renderAsync(defaultKernel, arguments, null).block();

		var collection = vectorStore.getCollection(VECTORSTORE_COLLECTION_NAME, collectionOptions);
		collection.createCollectionIfNotExistsAsync().block();

		// Similar functionality provided by VectorStoreTextSearch.getSearchResultsAsync
		var promptEmbedding = embeddingGenerationService.generateEmbeddingAsync(userPrompt).block();
		var retrievalResult = collection.searchAsync(promptEmbedding.getVector(),
				VectorSearchOptions.createDefault("embedding")).block();
		return ((VectorSearchResults<DocumentEmbedding>)retrievalResult).getResults().stream()
				.map(r -> r.getRecord().getContent()).toList();
	}

	private Recipe fetchRecipeWithRagAndToolCallingFor(String ingredientsStr) throws IOException {
		var systemPrompt = fixJsonResponsePromptResource.getContentAsString(StandardCharsets.UTF_8);
		var ragSystemPrompt = preferOwnRecipePromptResource.getContentAsString(StandardCharsets.UTF_8);
		var userPromptTemplate = recipeForAvailableIngredientsPromptResource.getContentAsString(StandardCharsets.UTF_8);
		var arguments = KernelFunctionArguments.builder().withVariable("ingredients", ingredientsStr).build();

		// Retrieve
		var retrieveDocumentsTool = kernelWithToolCalling.getFunction(RECIPE_SERVICE_TOOLS_KERNEL_PLUGIN_NAME, TOOL_NAME_RECIPE_DOCUMENTS);
		var functionArguments = KernelFunctionArguments.builder().withVariable("userPromptTemplate", userPromptTemplate).withVariable("ingredientsStr", ingredientsStr).build();
		List<String> documents = retrieveDocumentsTool.invokeAsync(kernelWithToolCalling)
				.withArguments(functionArguments)
				.withResultType(List.class).block().getResult();

		// Augment
		var combinedPromptTemplate = String.join("\n\n",
				Stream.concat(documents.stream(),
						List.of(systemPrompt, userPromptTemplate, ragSystemPrompt).stream()).toList());

		var promptExecutionSettings = PromptExecutionSettings.builder()
				.withJsonSchemaResponseFormat(Recipe.class)
				.withMaxTokens(800)
				.build();

		var fetchIngredientsAvailableAtHomeTool = kernelWithToolCalling.getFunction(RECIPE_SERVICE_TOOLS_KERNEL_PLUGIN_NAME, "fetch_ingredients_available_at_home");
		var invocationContext = InvocationContext.builder()
				.withPromptExecutionSettings(promptExecutionSettings)
				.withToolCallBehavior(ToolCallBehavior.requireKernelFunction(fetchIngredientsAvailableAtHomeTool))
				.build();

		return 	kernelWithToolCalling.invokePromptAsync(combinedPromptTemplate, arguments, invocationContext)
				.withResultTypeAutoConversion(Recipe.class)
				.block().getResult();
	}
}