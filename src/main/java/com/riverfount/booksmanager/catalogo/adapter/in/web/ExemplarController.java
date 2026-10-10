package com.riverfount.booksmanager.catalogo.adapter.in.web;

import com.riverfount.booksmanager.catalogo.application.port.in.CadastrarExemplarCommand;
import com.riverfount.booksmanager.catalogo.application.port.in.CadastrarExemplarUseCase;
import com.riverfount.booksmanager.catalogo.application.port.in.ConsultarExemplarUseCase;
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
@RequestMapping("/api/v1/exemplares")
class ExemplarController {

    private final CadastrarExemplarUseCase cadastrarExemplarUseCase;
    private final ConsultarExemplarUseCase consultarExemplarUseCase;

    ExemplarController(CadastrarExemplarUseCase cadastrarExemplarUseCase,
            ConsultarExemplarUseCase consultarExemplarUseCase) {
        this.cadastrarExemplarUseCase = cadastrarExemplarUseCase;
        this.consultarExemplarUseCase = consultarExemplarUseCase;
    }

    @PostMapping
    ResponseEntity<ExemplarResponse> cadastrar(@Valid @RequestBody CadastrarExemplarRequest request) {
        var exemplar = cadastrarExemplarUseCase.cadastrar(
                new CadastrarExemplarCommand(request.livroId(), request.codigoPatrimonio()));
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(exemplar.getId())
                .toUri();
        return ResponseEntity.created(location).body(ExemplarResponse.de(exemplar));
    }

    @GetMapping("/{id}")
    ExemplarResponse buscarPorId(@PathVariable Long id) {
        return consultarExemplarUseCase.buscarPorId(id)
                .map(ExemplarResponse::de)
                .orElseThrow(() -> new RecursoNaoEncontradoException("exemplar não encontrado: " + id));
    }
}
