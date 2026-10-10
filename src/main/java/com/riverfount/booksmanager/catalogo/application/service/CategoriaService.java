package com.riverfount.booksmanager.catalogo.application.service;

import com.riverfount.booksmanager.catalogo.application.port.in.CadastrarCategoriaCommand;
import com.riverfount.booksmanager.catalogo.application.port.in.CadastrarCategoriaUseCase;
import com.riverfount.booksmanager.catalogo.application.port.in.ConsultarCategoriaUseCase;
import com.riverfount.booksmanager.catalogo.application.port.out.CategoriaRepository;
import com.riverfount.booksmanager.catalogo.domain.Categoria;
import com.riverfount.booksmanager.compartilhado.domain.RegraDeNegocioException;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquestra o cadastro e a consulta de Categoria: carrega, chama o
 * domínio e persiste. Nenhuma regra de negócio própria (RN14 e a
 * validação de nome ficam em Categoria).
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
        if (categoriaRepository.existeComNome(command.nome())) {
            throw new RegraDeNegocioException("já existe uma categoria com o nome '" + command.nome() + "'");
        }
        return categoriaRepository.salvar(Categoria.novo(command.nome()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Categoria> buscarPorId(Long id) {
        return categoriaRepository.buscarPorId(id);
    }
}
