package com.riverfount.booksmanager.catalogo.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.catalogo.application.port.in.CadastrarLivroCommand;
import com.riverfount.booksmanager.catalogo.domain.Autor;
import com.riverfount.booksmanager.catalogo.domain.Categoria;
import com.riverfount.booksmanager.catalogo.domain.Isbn;
import com.riverfount.booksmanager.compartilhado.domain.RegraDeNegocioException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LivroServiceTest {

    private static final Clock RELOGIO_FIXO = Clock.fixed(Instant.parse("2026-10-10T00:00:00Z"), ZoneOffset.UTC);

    private LivroService service;
    private CategoriaRepositoryEmMemoria categoriaRepository;
    private AutorRepositoryEmMemoria autorRepository;
    private Long categoriaId;
    private Long autorId;

    @BeforeEach
    void preparar() {
        var livroRepository = new LivroRepositoryEmMemoria();
        categoriaRepository = new CategoriaRepositoryEmMemoria();
        autorRepository = new AutorRepositoryEmMemoria();
        service = new LivroService(livroRepository, categoriaRepository, autorRepository, RELOGIO_FIXO);

        categoriaId = categoriaRepository.salvar(Categoria.novo("Ficção")).getId();
        autorId = autorRepository.salvar(Autor.novo("Machado de Assis")).getId();
    }

    @Test
    void deveCadastrarLivroComDadosValidos() {
        var livro = service.cadastrar(new CadastrarLivroCommand("9788533302273", "Dom Casmurro", "Editora X", 1899,
                categoriaId, Set.of(autorId)));

        assertThat(livro.getId()).isNotNull();
        assertThat(livro.getIsbn()).isEqualTo(new Isbn("9788533302273"));
        assertThat(livro.getTitulo()).isEqualTo("Dom Casmurro");
        assertThat(livro.getCategoriaId()).isEqualTo(categoriaId);
        assertThat(livro.getAutorIds()).containsExactly(autorId);
    }

    @Test
    void naoDeveCadastrarComCategoriaInexistente() {
        assertThatThrownBy(() -> service.cadastrar(new CadastrarLivroCommand("9788533302273", "Dom Casmurro",
                "Editora X", 1899, 999999L, Set.of(autorId))))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void naoDeveCadastrarComAutorInexistente() {
        assertThatThrownBy(() -> service.cadastrar(new CadastrarLivroCommand("9788533302273", "Dom Casmurro",
                "Editora X", 1899, categoriaId, Set.of(999999L))))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void naoDeveCadastrarComCategoriaInativa() {
        var categoriaInativaId = 500L;
        categoriaRepository.salvar(Categoria.reconstituir(categoriaInativaId, "Categoria inativa", false));

        assertThatThrownBy(() -> service.cadastrar(new CadastrarLivroCommand("9788533302273", "Dom Casmurro",
                "Editora X", 1899, categoriaInativaId, Set.of(autorId))))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void naoDeveCadastrarComAutorInativo() {
        var autorInativoId = 500L;
        autorRepository.salvar(Autor.reconstituir(autorInativoId, "Autor inativo", false));

        assertThatThrownBy(() -> service.cadastrar(new CadastrarLivroCommand("9788533302273", "Dom Casmurro",
                "Editora X", 1899, categoriaId, Set.of(autorInativoId))))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void naoDeveCadastrarComIsbnDuplicado() {
        service.cadastrar(new CadastrarLivroCommand("9788533302273", "Dom Casmurro", "Editora X", 1899, categoriaId,
                Set.of(autorId)));

        assertThatThrownBy(() -> service.cadastrar(new CadastrarLivroCommand("9788533302273", "Outro Livro",
                "Editora Y", 1900, categoriaId, Set.of(autorId))))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void naoDeveCadastrarComAnoDePublicacaoFuturo() {
        assertThatThrownBy(() -> service.cadastrar(new CadastrarLivroCommand("9788533302273", "Dom Casmurro",
                "Editora X", 2027, categoriaId, Set.of(autorId))))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void deveConsultarLivroExistentePorId() {
        var cadastrado = service.cadastrar(new CadastrarLivroCommand("9788533302273", "Dom Casmurro", "Editora X",
                1899, categoriaId, Set.of(autorId)));

        var encontrado = service.buscarPorId(cadastrado.getId());

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().getTitulo()).isEqualTo("Dom Casmurro");
    }

    @Test
    void deveRetornarVazioAoConsultarIdInexistente() {
        assertThat(service.buscarPorId(-1L)).isEmpty();
    }
}
