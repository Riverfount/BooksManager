package com.riverfount.booksmanager.catalogo.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.catalogo.application.port.in.CadastrarCategoriaCommand;
import com.riverfount.booksmanager.compartilhado.domain.RegraDeNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CategoriaServiceTest {

    private CategoriaService service;

    @BeforeEach
    void preparar() {
        service = new CategoriaService(new CategoriaRepositoryEmMemoria());
    }

    @Test
    void deveCadastrarCategoriaNovaComId() {
        var categoria = service.cadastrar(new CadastrarCategoriaCommand("Ficção"));

        assertThat(categoria.getId()).isNotNull();
        assertThat(categoria.getNome()).isEqualTo("Ficção");
        assertThat(categoria.isAtivo()).isTrue();
    }

    @Test
    void naoDeveCadastrarCategoriaComNomeJaExistente() {
        service.cadastrar(new CadastrarCategoriaCommand("Ficção"));

        assertThatThrownBy(() -> service.cadastrar(new CadastrarCategoriaCommand("Ficção")))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void deveConsultarCategoriaExistentePorId() {
        var cadastrada = service.cadastrar(new CadastrarCategoriaCommand("Suspense"));

        var encontrada = service.buscarPorId(cadastrada.getId());

        assertThat(encontrada).isPresent();
        assertThat(encontrada.get().getNome()).isEqualTo("Suspense");
    }

    @Test
    void deveRetornarVazioAoConsultarIdInexistente() {
        assertThat(service.buscarPorId(-1L)).isEmpty();
    }
}
