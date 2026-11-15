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
       Estágios do orientador para a tela de validações, numa ordem fixa: o alterado há mais tempo
       primeiro, e no empate o de menor id. Sem o OrderBy, o banco devolvia em qualquer ordem e a
       lista mudava de posição entre um acesso e outro
    */
    List<Estagio> findByOrientadorEmailAndStatusInOrderByAlteradoEmAscIdAsc(String email, List<StatusEstagio> statuses);

    // Estágios do orientador para a tela de acompanhamento, do cadastrado há mais tempo para o mais recente
    List<Estagio> findByOrientadorEmailAndStatusInOrderByIdAsc(String email, List<StatusEstagio> statuses);

    /*
       Esse aqui é o mais importante da segurança, busca o estágio pelo id
       E pelo e-mail do orientador juntos. Assim o orientador só consegue
       achar estágios que pertencem a ele, mesmo que tente passar o id de
       outro estágio na URL
    */
    Optional<Estagio> findByIdAndOrientadorEmail(Long id, String email);

    // Estágios das matrículas de uma turma em certos status, usado para passar os estágios em aberto ao novo orientador (RF13)
    List<Estagio> findByMatriculaTurmaIdAndStatusIn(Long turmaId, Collection<StatusEstagio> statuses);
}