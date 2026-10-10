package com.riverfount.booksmanager.catalogo.adapter.in.web;

import com.riverfount.booksmanager.catalogo.application.port.in.CadastrarLivroCommand;
import com.riverfount.booksmanager.catalogo.application.port.in.CadastrarLivroUseCase;
import com.riverfount.booksmanager.catalogo.application.port.in.ConsultarLivroUseCase;
import com.riverfount.booksmanager.compartilhado.domain.RecursoNaoEncontradoException;
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
@RequestMapping("/api/v1/livros")
class LivroController {

    private final CadastrarLivroUseCase cadastrarLivroUseCase;
    private final ConsultarLivroUseCase consultarLivroUseCase;

    LivroController(CadastrarLivroUseCase cadastrarLivroUseCase, ConsultarLivroUseCase consultarLivroUseCase) {
        this.cadastrarLivroUseCase = cadastrarLivroUseCase;
        this.consultarLivroUseCase = consultarLivroUseCase;
    }

    @PostMapping
    ResponseEntity<LivroResponse> cadastrar(@Valid @RequestBody CadastrarLivroRequest request) {
        var livro = cadastrarLivroUseCase.cadastrar(new CadastrarLivroCommand(request.isbn(), request.titulo(),
                request.editora(), request.anoPublicacao(), request.categoriaId(), request.autorIds()));
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(livro.getId())
                .toUri();
        return ResponseEntity.created(location).body(LivroResponse.de(livro));
    }

    @GetMapping("/{id}")
    LivroResponse buscarPorId(@PathVariable Long id) {
        return consultarLivroUseCase.buscarPorId(id)
                .map(LivroResponse::de)
                .orElseThrow(() -> new RecursoNaoEncontradoException("livro não encontrado: " + id));
    }
}
