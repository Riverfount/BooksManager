package com.riverfount.booksmanager.catalogo.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.catalogo.application.port.in.CadastrarAutorCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AutorServiceTest {

    private AutorService service;

    @BeforeEach
    void preparar() {
        service = new AutorService(new AutorRepositoryEmMemoria());
    }

    @Test
    void deveCadastrarAutorNovoComId() {
        var autor = service.cadastrar(new CadastrarAutorCommand("Machado de Assis"));

        assertThat(autor.getId()).isNotNull();
        assertThat(autor.getNome()).isEqualTo("Machado de Assis");
        assertThat(autor.isAtivo()).isTrue();
    }

    @Test
    void deveAceitarDoisAutoresComOMesmoNome() {
        var primeiro = service.cadastrar(new CadastrarAutorCommand("José Silva"));
        var segundo = service.cadastrar(new CadastrarAutorCommand("José Silva"));

        assertThat(primeiro.getId()).isNotEqualTo(segundo.getId());
    }

    @Test
    void deveConsultarAutorExistentePorId() {
        var cadastrado = service.cadastrar(new CadastrarAutorCommand("Machado de Assis"));

        var encontrado = service.buscarPorId(cadastrado.getId());

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().getNome()).isEqualTo("Machado de Assis");
    }

    @Test
    void deveRetornarVazioAoConsultarIdInexistente() {
        assertThat(service.buscarPorId(-1L)).isEmpty();
    }

    @Test
    void deveRejeitarIdNuloAoConsultar() {
        assertThatThrownBy(() -> service.buscarPorId(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
