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

import com.riverfount.booksmanager.catalogo.application.port.in.CadastrarAutorUseCase;
import com.riverfount.booksmanager.catalogo.application.port.in.ConsultarAutorUseCase;
import com.riverfount.booksmanager.catalogo.domain.Autor;
import com.riverfount.booksmanager.compartilhado.adapter.in.web.TratadorDeExcecaoGlobal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AutorController.class)
@Import(TratadorDeExcecaoGlobal.class)
class AutorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CadastrarAutorUseCase cadastrarAutorUseCase;

    @MockitoBean
    private ConsultarAutorUseCase consultarAutorUseCase;

    @Test
    @WithMockUser
    void deveCadastrarAutorEDevolver201ComLocation() throws Exception {
        given(cadastrarAutorUseCase.cadastrar(any())).willReturn(Autor.reconstituir(1L, "Machado de Assis", true));

        mockMvc.perform(post("/api/v1/autores")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Machado de Assis\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/autores/1")))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nome").value("Machado de Assis"))
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    @WithMockUser
    void deveDevolver400ComProblemDetailParaNomeEmBranco() throws Exception {
        mockMvc.perform(post("/api/v1/autores")
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
        mockMvc.perform(post("/api/v1/autores")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\" Machado de Assis\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithMockUser
    void deveDevolver400ParaNomeMaiorQueOLimiteDoDominio() throws Exception {
        var nomeUmCaractereAcimaDoLimite = "A".repeat(Autor.TAMANHO_MAXIMO_NOME + 1);

        mockMvc.perform(post("/api/v1/autores")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + nomeUmCaractereAcimaDoLimite + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }

    @Test
    @WithMockUser
    void deveBuscarAutorExistentePorId() throws Exception {
        given(consultarAutorUseCase.buscarPorId(eq(1L)))
                .willReturn(Optional.of(Autor.reconstituir(1L, "Machado de Assis", true)));

        mockMvc.perform(get("/api/v1/autores/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nome").value("Machado de Assis"));
    }

    @Test
    @WithMockUser
    void deveDevolver404ParaIdInexistente() throws Exception {
        given(consultarAutorUseCase.buscarPorId(eq(99L))).willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/autores/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/problem+json")));
    }
}
