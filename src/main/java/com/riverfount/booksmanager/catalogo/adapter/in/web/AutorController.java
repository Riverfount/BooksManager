package com.riverfount.booksmanager.catalogo.adapter.in.web;

import com.riverfount.booksmanager.catalogo.application.port.in.CadastrarAutorCommand;
import com.riverfount.booksmanager.catalogo.application.port.in.CadastrarAutorUseCase;
import com.riverfount.booksmanager.catalogo.application.port.in.ConsultarAutorUseCase;
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
@RequestMapping("/api/v1/autores")
class AutorController {

    private final CadastrarAutorUseCase cadastrarAutorUseCase;
    private final ConsultarAutorUseCase consultarAutorUseCase;

    AutorController(CadastrarAutorUseCase cadastrarAutorUseCase, ConsultarAutorUseCase consultarAutorUseCase) {
        this.cadastrarAutorUseCase = cadastrarAutorUseCase;
        this.consultarAutorUseCase = consultarAutorUseCase;
    }

    @PostMapping
    ResponseEntity<AutorResponse> cadastrar(@Valid @RequestBody CadastrarAutorRequest request) {
        var autor = cadastrarAutorUseCase.cadastrar(new CadastrarAutorCommand(request.nome()));
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(autor.getId())
                .toUri();
        return ResponseEntity.created(location).body(AutorResponse.de(autor));
    }

    @GetMapping("/{id}")
    AutorResponse buscarPorId(@PathVariable Long id) {
        return consultarAutorUseCase.buscarPorId(id)
                .map(AutorResponse::de)
                .orElseThrow(() -> new RecursoNaoEncontradoException("autor não encontrado: " + id));
    }
}
