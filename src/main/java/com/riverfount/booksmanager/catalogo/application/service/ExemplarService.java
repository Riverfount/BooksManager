package com.riverfount.booksmanager.catalogo.application.service;

import com.riverfount.booksmanager.catalogo.application.port.in.CadastrarExemplarCommand;
import com.riverfount.booksmanager.catalogo.application.port.in.CadastrarExemplarUseCase;
import com.riverfount.booksmanager.catalogo.application.port.in.ConsultarExemplarUseCase;
import com.riverfount.booksmanager.catalogo.application.port.out.ExemplarRepository;
import com.riverfount.booksmanager.catalogo.application.port.out.LivroRepository;
import com.riverfount.booksmanager.catalogo.domain.Exemplar;
import com.riverfount.booksmanager.compartilhado.domain.RegraDeNegocioException;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquestra o cadastro e a consulta de Exemplar: busca o que o domínio
 * precisa para decidir (o livro existe e está ativo? o código de
 * patrimônio já existe?), chama o domínio e persiste. A decisão de
 * negócio em si fica no domínio (Exemplar.novo,
 * Exemplar.validarCodigoPatrimonioUnico).
 */
@Service
@Transactional
class ExemplarService implements CadastrarExemplarUseCase, ConsultarExemplarUseCase {

    private final ExemplarRepository exemplarRepository;
    private final LivroRepository livroRepository;

    ExemplarService(ExemplarRepository exemplarRepository, LivroRepository livroRepository) {
        this.exemplarRepository = exemplarRepository;
        this.livroRepository = livroRepository;
    }

    @Override
    public Exemplar cadastrar(CadastrarExemplarCommand command) {
        // construído antes das checagens cruzadas: assim, livroId já está
        // garantidamente não nulo quando consultamos o LivroRepository.
        var exemplar = Exemplar.novo(command.livroId(), command.codigoPatrimonio());
        validarLivroExisteEAtivo(exemplar.getLivroId());
        Exemplar.validarCodigoPatrimonioUnico(exemplar.getCodigoPatrimonio(),
                exemplarRepository.existeCodigoPatrimonio(exemplar.getCodigoPatrimonio()));
        return exemplarRepository.salvar(exemplar);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Exemplar> buscarPorId(Long id) {
        return exemplarRepository.buscarPorId(id);
    }

    private void validarLivroExisteEAtivo(Long livroId) {
        var livro = livroRepository.buscarPorId(livroId);
        if (livro.isEmpty()) {
            throw new RegraDeNegocioException("livro não encontrado: " + livroId);
        }
        if (!livro.get().isAtivo()) {
            throw new RegraDeNegocioException("livro inativo: " + livroId);
        }
    }
}
