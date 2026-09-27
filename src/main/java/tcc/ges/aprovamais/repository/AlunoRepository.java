package tcc.ges.aprovamais.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tcc.ges.aprovamais.entity.Aluno;

import java.util.Optional;

@Repository
public interface AlunoRepository extends JpaRepository<Aluno, Long> {

    // Busca um aluno pelo e-mail, usado no login e em outras partes do sistema
    Optional<Aluno> findByEmail(String email);

    // Verifica se já existe aluno com esse rgm, usado pra evitar duplicidade no cadastro
    boolean existsByRgm(String rgm);
}
