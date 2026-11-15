package tcc.ges.aprovamais.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tcc.ges.aprovamais.entity.Estagio;
import tcc.ges.aprovamais.entity.enums.StatusEstagio;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface EstagioRepository extends JpaRepository<Estagio, Long> {

    // Lista estágios por status, usado em painéis e relatórios do coordenador
    List<Estagio> findByStatus(StatusEstagio status);

    //Esse busca o atual, passando os status em aberto. Esse OrderByIdDesc é só garantia
    Optional<Estagio> findFirstByMatriculaIdAndStatusInOrderByIdDesc(Long matriculaId,
                                                                     Collection<StatusEstagio> statuses);

    // Histórico completo de estágios da matrícula, do mais antigo para o mais recente
    List<Estagio> findByMatriculaIdOrderByIdAsc(Long matriculaId);

    // Busca estágio por matrícula e status, útil para pegar o ativo de um aluno
    Optional<Estagio> findByMatriculaIdAndStatus(Long matriculaId, StatusEstagio status);

    /*
       Estágios do orientador para a tela de validações, do alterado há mais tempo para o mais recente,
       e no empate pelo menor id
    */
    List<Estagio> findByOrientadorEmailAndStatusInOrderByAlteradoEmAscIdAsc(String email, List<StatusEstagio> statuses);

    // Estágios do orientador para a tela de acompanhamento, do cadastrado há mais tempo para o mais recente
    List<Estagio> findByOrientadorEmailAndStatusInOrderByIdAsc(String email, List<StatusEstagio> statuses);

    /*
       Busca o estágio pelo id e pelo e-mail do orientador juntos. Assim o orientador só acha
       os próprios estágios, mesmo que passe o id de outro na URL
    */
    Optional<Estagio> findByIdAndOrientadorEmail(Long id, String email);

    // Estágios das matrículas de uma turma nesses status, usado para passar os estágios em aberto ao novo orientador
    List<Estagio> findByMatriculaTurmaIdAndStatusIn(Long turmaId, Collection<StatusEstagio> statuses);

    // Estágios de um aluno nesses status, usado na anonimização
    List<Estagio> findByMatriculaAlunoIdAndStatusIn(Long alunoId, Collection<StatusEstagio> statuses);

    // Estágios de um orientador nesses status, usado na anonimização
    List<Estagio> findByOrientadorIdAndStatusIn(Long orientadorId, Collection<StatusEstagio> statuses);
}
