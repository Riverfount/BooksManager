package com.riverfount.booksmanager.catalog.adapter.in.web;

import com.riverfount.booksmanager.catalog.application.port.in.GetCopyUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterCopyCommand;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterCopyUseCase;
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
@RequestMapping("/api/v1/copies")
class CopyController {

    private final RegisterCopyUseCase registerCopyUseCase;
    private final GetCopyUseCase getCopyUseCase;

    CopyController(RegisterCopyUseCase registerCopyUseCase, GetCopyUseCase getCopyUseCase) {
        this.registerCopyUseCase = registerCopyUseCase;
        this.getCopyUseCase = getCopyUseCase;
    }

    @PostMapping
    ResponseEntity<CopyResponse> register(@Valid @RequestBody RegisterCopyRequest request) {
        var copy = registerCopyUseCase.register(
                new RegisterCopyCommand(request.bookId(), request.assetCode()));
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(copy.getId())
                .toUri();
        return ResponseEntity.created(location).body(CopyResponse.from(copy));
    }

    @GetMapping("/{id}")
    CopyResponse findById(@PathVariable Long id) {
        return getCopyUseCase.findById(id)
                .map(CopyResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("exemplar não encontrado: " + id));
    }
}
