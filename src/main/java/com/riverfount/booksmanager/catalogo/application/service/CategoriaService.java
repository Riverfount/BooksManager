package com.riverfount.booksmanager.catalogo.application.service;

import com.riverfount.booksmanager.catalogo.application.port.in.CadastrarCategoriaCommand;
import com.riverfount.booksmanager.catalogo.application.port.in.CadastrarCategoriaUseCase;
import com.riverfount.booksmanager.catalogo.application.port.in.ConsultarCategoriaUseCase;
import com.riverfount.booksmanager.catalogo.application.port.out.CategoriaRepository;
import com.riverfount.booksmanager.catalogo.domain.Categoria;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquestra o cadastro e a consulta de Categoria: busca o que o domínio
 * precisa para decidir, chama o domínio e persiste. A decisão de negócio
 * em si (nome único, validações, RN14) fica inteira em Categoria.
 */
@Service
@Transactional
class CategoriaService implements CadastrarCategoriaUseCase, ConsultarCategoriaUseCase {

    private final CategoriaRepository categoriaRepository;

    CategoriaService(CategoriaRepository categoriaRepository) {
        this.categoriaRepository = categoriaRepository;
    }

    @Override
    public Categoria cadastrar(CadastrarCategoriaCommand command) {
        Categoria.validarNomeUnico(command.nome(), categoriaRepository.existeComNome(command.nome()));
        return categoriaRepository.salvar(Categoria.novo(command.nome()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Categoria> buscarPorId(Long id) {
        return categoriaRepository.buscarPorId(id);
    }
}
