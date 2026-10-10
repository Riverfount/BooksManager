package com.riverfount.booksmanager.catalog.adapter.in.web;

import com.riverfount.booksmanager.catalog.application.port.in.GetCategoryUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterCategoryCommand;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterCategoryUseCase;
import com.riverfount.booksmanager.shared.domain.ResourceNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/categories")
class CategoryController {

    private final RegisterCategoryUseCase registerCategoryUseCase;
    private final GetCategoryUseCase getCategoryUseCase;

    CategoryController(RegisterCategoryUseCase registerCategoryUseCase,
            GetCategoryUseCase getCategoryUseCase) {
        this.registerCategoryUseCase = registerCategoryUseCase;
        this.getCategoryUseCase = getCategoryUseCase;
    }

    @PostMapping
    ResponseEntity<CategoryResponse> register(@Valid @RequestBody RegisterCategoryRequest request) {
        var category = registerCategoryUseCase.register(new RegisterCategoryCommand(request.name()));
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(category.getId())
                .toUri();
        return ResponseEntity.created(location).body(CategoryResponse.from(category));
    }

    @GetMapping("/{id}")
    CategoryResponse findById(@PathVariable Long id) {
        return getCategoryUseCase.findById(id)
                .map(CategoryResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("categoria não encontrada: " + id));
    }
}
