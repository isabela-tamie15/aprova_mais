package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.entity.Aluno;
import tcc.ges.aprovamais.entity.Orientador;
import tcc.ges.aprovamais.entity.Secretaria;
import tcc.ges.aprovamais.entity.Usuario;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.AlunoRepository;
import tcc.ges.aprovamais.repository.OrientadorRepository;
import tcc.ges.aprovamais.repository.SecretariaRepository;
import tcc.ges.aprovamais.repository.UsuarioRepository;
import java.util.Optional;
import java.util.UUID;

// Essa service é quem cuida da anonimização de dados quando o titular pede exclusão pela LGPD
@Service
@RequiredArgsConstructor
public class AnonimizacaoService {

    private final UsuarioRepository usuarioRepository;
    private final AlunoRepository alunoRepository;
    private final OrientadorRepository orientadorRepository;
    private final SecretariaRepository secretariaRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;

    /*
       Esse é o método que faz a anonimização em si. A ideia não é apagar
       o registro do banco, e sim trocar os dados pessoais por valores
       genéricos, assim o histórico fica preservado para fins estatísticos
       mas ninguém consegue identificar quem era a pessoa
    */
    @Transactional
    public void anonimizar(String email, String ipOrigem) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        /*
           Gera um sufixo aleatório de 8 caracteres. Ele é usado no e-mail e
           em outros campos para garantir que, se dois usuários forem anonimizados,
           cada um fica com um valor único e não dá conflito de unicidade no banco
        */
        String sufixo = UUID.randomUUID().toString().substring(0, 8);

        /*
           Troca os dados principais do usuário. A senha vira um hash de um
           UUID aleatório, ou seja, ninguém mais consegue logar nessa conta
           nem que saiba a senha original
        */
        usuario.setNome("USUÁRIO REMOVIDO");
        usuario.setEmail("removido_" + sufixo + "@anonimizado.br");
        usuario.setSenhaHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        usuario.setAtivo(false);
        usuario.setConsentimentoDado(false);
        usuario.setTokenRecuperacao(null);
        usuario.setDoisFatoresSegredo(null);

        /*
           Agora tratamos os campos específicos de cada perfil. Cada tipo de
           usuário tem dados próprios em tabelas separadas, então limpamos
           essas tabelas também, cada uma do seu jeito
        */
        switch (usuario.getPerfil()) {
            case ALUNO -> {
                Optional<Aluno> alunoEncontrado = alunoRepository.findById(usuario.getId());
                if (alunoEncontrado.isPresent()) {
                    Aluno aluno = alunoEncontrado.get();
                    aluno.setRgm("REMOVIDO_" + sufixo);
                    alunoRepository.save(aluno);
                }
            }
            case ORIENTADOR -> {
                Optional<Orientador> orientadorEncontrado = orientadorRepository.findById(usuario.getId());
                if (orientadorEncontrado.isPresent()) {
                    Orientador orientador = orientadorEncontrado.get();
                    orientador.setMatriculaInstitucional("REMOVIDO_" + sufixo);
                    orientador.setDepartamento("REMOVIDO");
                    orientadorRepository.save(orientador);
                }
            }
            case SECRETARIA -> {
                Optional<Secretaria> secretariaEncontrada = secretariaRepository.findById(usuario.getId());
                if (secretariaEncontrada.isPresent()) {
                    Secretaria secretaria = secretariaEncontrada.get();
                    secretaria.setSetor("REMOVIDO");
                    secretariaRepository.save(secretaria);
                }
            }
            case COORDENADOR, ADMINISTRADOR -> {
                // Coordenador e administrador não têm dados pessoais em tabela própria
            }
        }

        usuarioRepository.save(usuario);

        // Registra na auditoria que a anonimização foi feita, com o IP de quem solicitou
        auditoriaService.registrar(
                usuario,
                "ANONIMIZACAO_EXECUTADA",
                "Dados pessoais anonimizados por solicitação do titular",
                ipOrigem,
                true
        );
    }

    /*
       Registra na auditoria o pedido de acesso ou de correção dos dados pessoais.
       Os dados em si são entregues pela secretaria, fora do sistema
    */
    // Grava o registro de auditoria, então não é só leitura
    @Transactional
    public void registrarSolicitacao(String email, String acao, String detalhes, String ipOrigem) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        auditoriaService.registrar(usuario, acao, detalhes, ipOrigem, true);
    }
}
