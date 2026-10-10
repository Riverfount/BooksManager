package com.riverfount.booksmanager.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifica o esquema criado pela migração V1__criar_catalogo.sql (RNF03):
 * as tabelas e as restrições que reforçam as regras de negócio do catálogo.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MigracaoCatalogoTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void devePossuirAsTabelasDoCatalogo() {
        for (String tabela : new String[] {"categoria", "autor", "livro", "livro_autor", "exemplar"}) {
            assertThat(contaTabela(tabela)).as("tabela %s", tabela).isEqualTo(1);
        }
    }

    @Test
    void devePossuirAMigracaoRegistradaComSucesso() {
        var sucesso = jdbcTemplate.queryForObject(
                "select success from flyway_schema_history where script = 'V1__criar_catalogo.sql'",
                Boolean.class);

        assertThat(sucesso).isTrue();
    }

    @Test
    void nomeDaCategoriaDeveSerUnico() {
        assertThat(possuiRestricaoUnica("categoria", "nome")).isTrue();
    }

    @Test
    void isbnDoLivroDeveSerUnicoEVarchar13() {
        assertThat(possuiRestricaoUnica("livro", "isbn")).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                """
                select character_maximum_length from information_schema.columns
                where table_name = 'livro' and column_name = 'isbn'
                """,
                Integer.class)).isEqualTo(13);
    }

    @Test
    void livroDeveReferenciarCategoriaPorChaveEstrangeira() {
        assertThat(possuiChaveEstrangeira("livro", "categoria_id", "categoria")).isTrue();
    }

    @Test
    void livroAutorDeveTerChavePrimariaCompostaEChavesEstrangeiras() {
        var colunasDaChave = jdbcTemplate.queryForList(
                """
                select kcu.column_name from information_schema.table_constraints tc
                join information_schema.key_column_usage kcu on tc.constraint_name = kcu.constraint_name
                where tc.table_name = 'livro_autor' and tc.constraint_type = 'PRIMARY KEY'
                order by kcu.ordinal_position
                """,
                String.class);

        assertThat(colunasDaChave).containsExactly("livro_id", "autor_id");
        assertThat(possuiChaveEstrangeira("livro_autor", "livro_id", "livro")).isTrue();
        assertThat(possuiChaveEstrangeira("livro_autor", "autor_id", "autor")).isTrue();
    }

    @Test
    void exemplarDeveReferenciarLivroETerCodigoDePatrimonioUnico() {
        assertThat(possuiChaveEstrangeira("exemplar", "livro_id", "livro")).isTrue();
        assertThat(possuiRestricaoUnica("exemplar", "codigo_patrimonio")).isTrue();
    }

    @Test
    void exemplarDeveTerVersaoBigintNaoNulaComPadraoZero() {
        var coluna = jdbcTemplate.queryForMap(
                """
                select data_type, is_nullable, column_default from information_schema.columns
                where table_name = 'exemplar' and column_name = 'versao'
                """);

        assertThat(coluna.get("data_type")).isEqualTo("bigint");
        assertThat(coluna.get("is_nullable")).isEqualTo("NO");
        assertThat((String) coluna.get("column_default")).startsWith("0");
    }

    @Test
    void exemplarDeveRejeitarStatusForaDosCincoValoresValidos() {
        jdbcTemplate.update(
                "insert into categoria (nome, ativo) values ('Ficção', true)");
        jdbcTemplate.update(
                """
                insert into livro (isbn, titulo, editora, ano_publicacao, categoria_id, ativo)
                values ('9788533302273', 'Dom Casmurro', 'Editora', 1899,
                        (select id from categoria where nome = 'Ficção'), true)
                """);

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                insert into exemplar (livro_id, codigo_patrimonio, status, ativo, versao)
                values ((select id from livro where isbn = '9788533302273'), 'PAT-1', 'STATUS_INVALIDO', true, 0)
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deveTerIndiceEmLivroTituloEEmExemplarLivroId() {
        assertThat(contaIndice("livro", "titulo")).isGreaterThanOrEqualTo(1);
        assertThat(contaIndice("exemplar", "livro_id")).isGreaterThanOrEqualTo(1);
    }

    private int contaTabela(String tabela) {
        return jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public' and table_name = ?",
                Integer.class, tabela);
    }

    private boolean possuiRestricaoUnica(String tabela, String coluna) {
        var total = jdbcTemplate.queryForObject(
                """
                select count(*) from information_schema.table_constraints tc
                join information_schema.key_column_usage kcu on tc.constraint_name = kcu.constraint_name
                where tc.table_name = ? and kcu.column_name = ? and tc.constraint_type = 'UNIQUE'
                """,
                Integer.class, tabela, coluna);
        return total != null && total > 0;
    }

    private boolean possuiChaveEstrangeira(String tabela, String coluna, String tabelaReferenciada) {
        var total = jdbcTemplate.queryForObject(
                """
                select count(*) from information_schema.table_constraints tc
                join information_schema.key_column_usage kcu on tc.constraint_name = kcu.constraint_name
                join information_schema.constraint_column_usage ccu on tc.constraint_name = ccu.constraint_name
                where tc.table_name = ? and kcu.column_name = ? and tc.constraint_type = 'FOREIGN KEY'
                    and ccu.table_name = ?
                """,
                Integer.class, tabela, coluna, tabelaReferenciada);
        return total != null && total > 0;
    }

    private int contaIndice(String tabela, String coluna) {
        var total = jdbcTemplate.queryForObject(
                "select count(*) from pg_indexes where tablename = ? and indexdef ilike ?",
                Integer.class, tabela, "%(" + coluna + ")%");
        return total == null ? 0 : total;
    }
}
