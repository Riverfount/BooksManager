package com.riverfount.booksmanager.catalogo.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.riverfount.booksmanager.catalogo.application.port.in.CadastrarCategoriaUseCase;
import com.riverfount.booksmanager.catalogo.application.port.in.ConsultarCategoriaUseCase;
import com.riverfount.booksmanager.catalogo.domain.Categoria;
import com.riverfount.booksmanager.compartilhado.adapter.in.web.TratadorDeExcecaoGlobal;
import com.riverfount.booksmanager.compartilhado.domain.RegraDeNegocioException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CategoriaController.class)
@Import(TratadorDeExcecaoGlobal.class)
class CategoriaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CadastrarCategoriaUseCase cadastrarCategoriaUseCase;

    @MockitoBean
    private ConsultarCategoriaUseCase consultarCategoriaUseCase;

    @Test
    @WithMockUser
    void deveCadastrarCategoriaEDevolver201ComLocation() throws Exception {
        given(cadastrarCategoriaUseCase.cadastrar(any())).willReturn(Categoria.reconstituir(1L, "Ficção", true));

        mockMvc.perform(post("/api/v1/categorias")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Ficção\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/categorias/1")))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nome").value("Ficção"))
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    @WithMockUser
    void deveDevolver400ComProblemDetailParaNomeEmBranco() throws Exception {
        mockMvc.perform(post("/api/v1/categorias")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithMockUser
    void deveDevolver400ParaNomeComEspacoNaPonta() throws Exception {
        // o DTO precisa pegar isso como erro de formato (400), e não deixar
        // cair no domínio como regra de negócio (422)
        mockMvc.perform(post("/api/v1/categorias")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\" Ficção\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithMockUser
    void deveDevolver400ParaNomeMaiorQueOLimiteDoDominio() throws Exception {
        // o limite do DTO precisa ser o mesmo da regra de negócio
        // (Categoria.TAMANHO_MAXIMO_NOME), não um número duplicado à parte
        var nomeUmCaractereAcimaDoLimite = "A".repeat(Categoria.TAMANHO_MAXIMO_NOME + 1);

        mockMvc.perform(post("/api/v1/categorias")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + nomeUmCaractereAcimaDoLimite + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithMockUser
    void deveDevolver422ComMensagemClaraParaNomeDuplicado() throws Exception {
        given(cadastrarCategoriaUseCase.cadastrar(any()))
                .willThrow(new RegraDeNegocioException("já existe uma categoria com o nome 'Ficção'"));

        mockMvc.perform(post("/api/v1/categorias")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Ficção\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")))
                .andExpect(jsonPath("$.detail").value("já existe uma categoria com o nome 'Ficção'"));
    }

    @Test
    @WithMockUser
    void deveBuscarCategoriaExistentePorId() throws Exception {
        given(consultarCategoriaUseCase.buscarPorId(eq(1L)))
                .willReturn(Optional.of(Categoria.reconstituir(1L, "Ficção", true)));

        mockMvc.perform(get("/api/v1/categorias/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nome").value("Ficção"));
    }

    @Test
    @WithMockUser
    void deveDevolver404ParaIdInexistente() throws Exception {
        given(consultarCategoriaUseCase.buscarPorId(eq(99L))).willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/categorias/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }
}
