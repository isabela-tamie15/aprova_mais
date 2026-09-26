package tcc.ges.aprovamais.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tcc.ges.aprovamais.entity.Estagio;
import tcc.ges.aprovamais.entity.enums.StatusEstagio;

import java.util.List;
import java.util.Optional;

@Repository
public interface EstagioRepository extends JpaRepository<Estagio, Long> {

    // Lista estágios por status, usado em painéis e relatórios do coordenador
    List<Estagio> findByStatus(StatusEstagio status);

    // Busca o estágio pela matrícula, usado quando só pode existir um por matrícula
    Optional<Estagio> findByMatriculaId(Long matriculaId);

    // Busca estágio por matrícula e status, útil pra pegar o ativo de um aluno
    Optional<Estagio> findByMatriculaIdAndStatus(Long matriculaId, StatusEstagio status);

    // Lista estágios de um orientador filtrando por vários status, usado na tela de validações
    List<Estagio> findByOrientadorEmailAndStatusIn(String email, List<StatusEstagio> statuses);

    /*
       Esse aqui é o mais importante da segurança, busca o estágio pelo id
       E pelo e-mail do orientador juntos. Assim o orientador só consegue
       achar estágios que pertencem a ele, mesmo que tente passar o id de
       outro estágio na URL
    */
    Optional<Estagio> findByIdAndOrientadorEmail(Long id, String email);
}