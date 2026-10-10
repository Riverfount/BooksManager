package com.riverfount.booksmanager.catalogo.adapter.in.web;

import com.riverfount.booksmanager.catalogo.application.port.in.CadastrarCategoriaCommand;
import com.riverfount.booksmanager.catalogo.application.port.in.CadastrarCategoriaUseCase;
import com.riverfount.booksmanager.catalogo.application.port.in.ConsultarCategoriaUseCase;
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
@RequestMapping("/api/v1/categorias")
class CategoriaController {

    private final CadastrarCategoriaUseCase cadastrarCategoriaUseCase;
    private final ConsultarCategoriaUseCase consultarCategoriaUseCase;

    CategoriaController(CadastrarCategoriaUseCase cadastrarCategoriaUseCase,
            ConsultarCategoriaUseCase consultarCategoriaUseCase) {
        this.cadastrarCategoriaUseCase = cadastrarCategoriaUseCase;
        this.consultarCategoriaUseCase = consultarCategoriaUseCase;
    }

    @PostMapping
    ResponseEntity<CategoriaResponse> cadastrar(@Valid @RequestBody CadastrarCategoriaRequest request) {
        var categoria = cadastrarCategoriaUseCase.cadastrar(new CadastrarCategoriaCommand(request.nome()));
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(categoria.getId())
                .toUri();
        return ResponseEntity.created(location).body(CategoriaResponse.de(categoria));
    }

    @GetMapping("/{id}")
    CategoriaResponse buscarPorId(@PathVariable Long id) {
        return consultarCategoriaUseCase.buscarPorId(id)
                .map(CategoriaResponse::de)
                .orElseThrow(() -> new RecursoNaoEncontradoException("categoria não encontrada: " + id));
    }
}
