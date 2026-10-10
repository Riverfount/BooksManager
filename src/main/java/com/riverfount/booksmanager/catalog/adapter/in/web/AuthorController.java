package com.riverfount.booksmanager.catalog.adapter.in.web;

import com.riverfount.booksmanager.catalog.application.port.in.GetAuthorUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterAuthorCommand;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterAuthorUseCase;
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
@RequestMapping("/api/v1/authors")
class AuthorController {

    private final RegisterAuthorUseCase registerAuthorUseCase;
    private final GetAuthorUseCase getAuthorUseCase;

    AuthorController(RegisterAuthorUseCase registerAuthorUseCase, GetAuthorUseCase getAuthorUseCase) {
        this.registerAuthorUseCase = registerAuthorUseCase;
        this.getAuthorUseCase = getAuthorUseCase;
    }

    @PostMapping
    ResponseEntity<AuthorResponse> register(@Valid @RequestBody RegisterAuthorRequest request) {
        var author = registerAuthorUseCase.register(new RegisterAuthorCommand(request.name()));
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(author.getId())
                .toUri();
        return ResponseEntity.created(location).body(AuthorResponse.from(author));
    }

    @GetMapping("/{id}")
    AuthorResponse findById(@PathVariable Long id) {
        return getAuthorUseCase.findById(id)
                .map(AuthorResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("autor não encontrado: " + id));
    }
}
