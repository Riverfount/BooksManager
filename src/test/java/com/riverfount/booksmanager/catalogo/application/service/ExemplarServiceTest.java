package com.riverfount.booksmanager.catalogo.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.catalogo.application.port.in.CadastrarExemplarCommand;
import com.riverfount.booksmanager.catalogo.domain.Isbn;
import com.riverfount.booksmanager.catalogo.domain.Livro;
import com.riverfount.booksmanager.compartilhado.domain.RegraDeNegocioException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExemplarServiceTest {

    private static final Clock RELOGIO_FIXO = Clock.fixed(Instant.parse("2026-10-10T00:00:00Z"), ZoneOffset.UTC);

    private ExemplarService service;
    private LivroRepositoryEmMemoria livroRepository;
    private Long livroId;

    @BeforeEach
    void preparar() {
        var exemplarRepository = new ExemplarRepositoryEmMemoria();
        livroRepository = new LivroRepositoryEmMemoria();
        service = new ExemplarService(exemplarRepository, livroRepository);

        var categoriaId = 1L;
        var autorId = 1L;
        var livro = Livro.novo(new Isbn("9788533302273"), "Dom Casmurro", "Editora X", 1899, categoriaId,
                Set.of(autorId), RELOGIO_FIXO);
        livroId = livroRepository.salvar(livro).getId();
    }

    @Test
    void deveCadastrarExemplarComDadosValidos() {
        var exemplar = service.cadastrar(new CadastrarExemplarCommand(livroId, "PAT-1"));

        assertThat(exemplar.getId()).isNotNull();
        assertThat(exemplar.getLivroId()).isEqualTo(livroId);
        assertThat(exemplar.getCodigoPatrimonio()).isEqualTo("PAT-1");
    }

    @Test
    void naoDeveCadastrarComLivroInexistente() {
        assertThatThrownBy(() -> service.cadastrar(new CadastrarExemplarCommand(999999L, "PAT-1")))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void naoDeveCadastrarComLivroInativo() {
        var livroInativoId = 500L;
        var livroInativo = Livro.reconstituir(livroInativoId, new Isbn("080442957X"), "Outro Livro", "Editora Y",
                1900, 1L, Set.of(1L), false);
        livroRepository.salvar(livroInativo);

        assertThatThrownBy(() -> service.cadastrar(new CadastrarExemplarCommand(livroInativoId, "PAT-1")))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void naoDeveCadastrarComCodigoPatrimonioDuplicado() {
        service.cadastrar(new CadastrarExemplarCommand(livroId, "PAT-1"));

        assertThatThrownBy(() -> service.cadastrar(new CadastrarExemplarCommand(livroId, "PAT-1")))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void deveConsultarExemplarExistentePorId() {
        var cadastrado = service.cadastrar(new CadastrarExemplarCommand(livroId, "PAT-1"));

        var encontrado = service.buscarPorId(cadastrado.getId());

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().getCodigoPatrimonio()).isEqualTo("PAT-1");
    }

    @Test
    void deveRetornarVazioAoConsultarIdInexistente() {
        assertThat(service.buscarPorId(-1L)).isEmpty();
    }
}
