package com.riverfount.booksmanager.catalog.adapter.in.web;

import com.riverfount.booksmanager.catalog.application.port.in.GetBookUseCase;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterBookCommand;
import com.riverfount.booksmanager.catalog.application.port.in.RegisterBookUseCase;
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
@RequestMapping("/api/v1/books")
class BookController {

    private final RegisterBookUseCase registerBookUseCase;
    private final GetBookUseCase getBookUseCase;

    BookController(RegisterBookUseCase registerBookUseCase, GetBookUseCase getBookUseCase) {
        this.registerBookUseCase = registerBookUseCase;
        this.getBookUseCase = getBookUseCase;
    }

    @PostMapping
    ResponseEntity<BookResponse> register(@Valid @RequestBody RegisterBookRequest request) {
        var book = registerBookUseCase.register(new RegisterBookCommand(request.isbn(), request.title(),
                request.publisher(), request.publicationYear(), request.categoryId(), request.authorIds()));
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(book.getId())
                .toUri();
        return ResponseEntity.created(location).body(BookResponse.from(book));
    }

    @GetMapping("/{id}")
    BookResponse findById(@PathVariable Long id) {
        return getBookUseCase.findById(id)
                .map(BookResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("livro não encontrado: " + id));
    }
}
