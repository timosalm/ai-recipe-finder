package com.example.recipe;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.image.ImageModel;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.springframework.util.StringUtils.capitalize;

@Controller
@RequestMapping("/")
class RecipeUiController {

    private static final Logger log = LoggerFactory.getLogger(RecipeUiController.class);

    private final RecipeService recipeService;
    private final Optional<ImageModel> imageModel;
    private final ChatModel chatModel;

    RecipeUiController(RecipeService recipeService, Optional<ImageModel> imageModel, ChatModel chatModel) {
        this.recipeService = recipeService;
        this.imageModel = imageModel;
		this.chatModel = chatModel;
	}

    @GetMapping
    String fetchUI(Model model) {
        var aiModelNames = getAiModelNames();
        model.addAttribute("aiModel", String.join(" & ", aiModelNames));
        if (!model.containsAttribute("fetchRecipeData")) {
            model.addAttribute("fetchRecipeData", new FetchRecipeData());
        }
        return "index";
    }

    @PostMapping
    String fetchRecipeUiFor(FetchRecipeData fetchRecipeData, Model model) throws Exception {
        Recipe recipe;
        try {
            recipe = recipeService.fetchRecipeFor(fetchRecipeData.ingredients(), fetchRecipeData.isPreferAvailableIngredients(), fetchRecipeData.isPreferOwnRecipes());
        } catch (Exception e) {
            log.info("Retry RecipeUiController:fetchRecipeFor after exception caused by LLM");
            recipe = recipeService.fetchRecipeFor(fetchRecipeData.ingredients(), fetchRecipeData.isPreferAvailableIngredients(), fetchRecipeData.isPreferOwnRecipes());
        }
        model.addAttribute("recipe", recipe);
        model.addAttribute("fetchRecipeData", fetchRecipeData);
        return fetchUI(model);
    }

    private List<String> getAiModelNames() {
        var modelNames = new ArrayList<String>();
        var chatModelProvider = chatModel.getClass().getSimpleName().replace("ChatModel", "");
        var chatModelDefaultOptions = chatModel.defaultRequestParameters();
        try {
            var modelName = (String)FieldUtils.readField(chatModelDefaultOptions, "modelName", true);
            modelNames.add("%s (%s)".formatted(chatModelProvider, capitalize(modelName)));
        } catch (Exception e1) {
            try {
                var modelName = (String)FieldUtils.readField(chatModelDefaultOptions, "deploymentName", true);
                modelNames.add("%s (%s)".formatted(chatModelProvider, capitalize(modelName)));
            } catch (Exception e2) {
                modelNames.add(chatModelProvider);
            }
        }

        if (imageModel.isPresent()) {
            var imageModelProvider = imageModel.get().getClass().getSimpleName().replace("ImageModel", "");
            try {
                var imageModelName = (String)FieldUtils.readField(imageModel.get(), "modelName", true);
                modelNames.add("%s (%s)".formatted(imageModelProvider, capitalize(imageModelName)));
            } catch (Exception e1) {
                try {
                    var imageModelName = (String)FieldUtils.readField(imageModel.get(), "deploymentName", true);
                    modelNames.add("%s (%s)".formatted(chatModelProvider, capitalize(imageModelName)));
                } catch (Exception e2) {
                    modelNames.add(imageModelProvider);
                }
            }
        }

        return modelNames;
    }
}
