package com.riverfount.booksmanager.catalogo.adapter.out.persistence;

import com.riverfount.booksmanager.catalogo.application.port.out.ExemplarRepository;
import com.riverfount.booksmanager.catalogo.domain.Exemplar;
import com.riverfount.booksmanager.compartilhado.adapter.out.persistence.ViolacoesDeRestricao;
import com.riverfount.booksmanager.compartilhado.domain.RegraDeNegocioException;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
class ExemplarPersistenceAdapter implements ExemplarRepository {

    private final ExemplarJpaRepository jpaRepository;

    ExemplarPersistenceAdapter(ExemplarJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Exemplar salvar(Exemplar exemplar) {
        var entidade = new ExemplarJpaEntity(exemplar.getId(), exemplar.getLivroId(),
                exemplar.getCodigoPatrimonio(), exemplar.getStatus(), exemplar.isAtivo(), exemplar.getVersao());
        try {
            // saveAndFlush, e não save: o lock otimista (RNF17) precisa
            // falhar aqui, de forma síncrona, e não só num flush adiado
            // (por exemplo, no commit da transação do chamador).
            var salvo = jpaRepository.saveAndFlush(entidade);
            return paraDominio(salvo);
        } catch (DataIntegrityViolationException e) {
            if (ViolacoesDeRestricao.viola(e, "exemplar_codigo_patrimonio_key")) {
                throw new RegraDeNegocioException(
                        "já existe um exemplar com o código de patrimônio '" + exemplar.getCodigoPatrimonio()
                                + "'");
            }
            throw e;
        }
    }

    @Override
    public Optional<Exemplar> buscarPorId(Long id) {
        return jpaRepository.findById(id).map(this::paraDominio);
    }

    @Override
    public boolean existeCodigoPatrimonio(String codigoPatrimonio) {
        return jpaRepository.existsByCodigoPatrimonio(codigoPatrimonio);
    }

    private Exemplar paraDominio(ExemplarJpaEntity entidade) {
        return Exemplar.reconstituir(entidade.getId(), entidade.getLivroId(), entidade.getCodigoPatrimonio(),
                entidade.getStatus(), entidade.isAtivo(), entidade.getVersao());
    }
}
