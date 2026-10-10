package com.riverfount.booksmanager.catalogo.application.service;

import com.riverfount.booksmanager.catalogo.application.port.in.CadastrarAutorCommand;
import com.riverfount.booksmanager.catalogo.application.port.in.CadastrarAutorUseCase;
import com.riverfount.booksmanager.catalogo.application.port.in.ConsultarAutorUseCase;
import com.riverfount.booksmanager.catalogo.application.port.out.AutorRepository;
import com.riverfount.booksmanager.catalogo.domain.Autor;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquestra o cadastro e a consulta de Autor: chama o domínio e persiste.
 * Sem checagem de nome único — autor.nome não tem essa restrição.
 */
@Service
@Transactional
class AutorService implements CadastrarAutorUseCase, ConsultarAutorUseCase {

    private final AutorRepository autorRepository;

    AutorService(AutorRepository autorRepository) {
        this.autorRepository = autorRepository;
    }

    @Override
    public Autor cadastrar(CadastrarAutorCommand command) {
        return autorRepository.salvar(Autor.novo(command.nome()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Autor> buscarPorId(Long id) {
        return autorRepository.buscarPorId(id);
    }
}
