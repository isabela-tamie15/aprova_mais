package tcc.ges.aprovamais.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tcc.ges.aprovamais.entity.PreCadastro;

import java.util.List;
import java.util.Optional;

@Repository
public interface PreCadastroRepository extends JpaRepository<PreCadastro, Long> {

    // Lista os pré-cadastros do coordenador logado, em ordem alfabética
    List<PreCadastro> findByCoordenadorEmailOrderByNomeAsc(String emailCoordenador);

    // Todos os pré-cadastros, em ordem alfabética, para a tela de convites da secretaria
    List<PreCadastro> findAllByOrderByNomeAsc();

    // Busca o pré-cadastro só se for do coordenador logado. O de outro coordenador volta vazio e vira 404
    Optional<PreCadastro> findByIdAndCoordenadorEmail(Long id, String emailCoordenador);

    // Usado no convite e no aceite, para achar a pessoa pelo e-mail que recebeu o link
    Optional<PreCadastro> findByEmailIgnoreCase(String email);

    // Confere se o e-mail já está no pré-cadastro, no cadastro e na edição
    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    // Duplicidade do RGM ou da matrícula institucional, no cadastro e na edição
    boolean existsByIdentificadorInstitucionalIgnoreCase(String identificador);

    boolean existsByIdentificadorInstitucionalIgnoreCaseAndIdNot(String identificador, Long id);
}
