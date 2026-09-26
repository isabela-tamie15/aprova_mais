package tcc.ges.aprovamais.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tcc.ges.aprovamais.entity.Usuario;
import tcc.ges.aprovamais.entity.enums.PerfilUsuario;

import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    // Busca o usuário pelo e-mail, usado no login e em várias outras partes
    Optional<Usuario> findByEmail(String email);

    // Versão booleana, mais rápida quando só quer saber se já existe alguém com esse e-mail
    boolean existsByEmail(String email);

    /*
       Busca o usuário pelo token de pré-autenticação do 2fa, usado na
       segunda etapa do login e na configuração obrigatória
    */
    Optional<Usuario> findByTokenPreAutenticacao(String tokenPreAutenticacaoHash);

    // Verifica se já existe pelo menos um usuário com esse perfil, usado pra saber se já tem secretaria cadastrada
    boolean existsByPerfil(PerfilUsuario perfil);
}
