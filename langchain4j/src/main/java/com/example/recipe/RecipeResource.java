package com.example.recipe;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/recipes")
class RecipeResource {

    private final RecipeService recipeService;

    RecipeResource(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    @PostMapping("upload")
    ResponseEntity<Void> addRecipeDocumentsForRag(@RequestParam("file") MultipartFile file) throws IOException {
        recipeService.addRecipeDocumentForRag(file.getResource());
        return ResponseEntity.noContent().build();
    }

}
