package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.dto.SolicitacaoTitularResponse;
import tcc.ges.aprovamais.entity.Aluno;
import tcc.ges.aprovamais.entity.Estagio;
import tcc.ges.aprovamais.entity.Orientador;
import tcc.ges.aprovamais.entity.PreCadastro;
import tcc.ges.aprovamais.entity.Secretaria;
import tcc.ges.aprovamais.entity.Usuario;
import tcc.ges.aprovamais.entity.enums.MotivoEncerramento;
import tcc.ges.aprovamais.entity.enums.PerfilUsuario;
import tcc.ges.aprovamais.entity.enums.StatusEstagio;
import tcc.ges.aprovamais.exception.DadoInvalidoException;
import tcc.ges.aprovamais.exception.RegraNegocioException;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.AlunoRepository;
import tcc.ges.aprovamais.repository.ConviteRepository;
import tcc.ges.aprovamais.repository.CursoRepository;
import tcc.ges.aprovamais.repository.EstagioRepository;
import tcc.ges.aprovamais.repository.LogAuditoriaRepository;
import tcc.ges.aprovamais.repository.OrientadorRepository;
import tcc.ges.aprovamais.repository.OrientadorTurmaRepository;
import tcc.ges.aprovamais.repository.PreCadastroRepository;
import tcc.ges.aprovamais.repository.SecretariaRepository;
import tcc.ges.aprovamais.repository.UsuarioRepository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

// Essa service é quem cuida da anonimização de dados quando o titular pede exclusão pela LGPD
@Service
@RequiredArgsConstructor
public class AnonimizacaoService {

    private static final Logger log = LoggerFactory.getLogger(AnonimizacaoService.class);

    static final String NOME_ANONIMIZADO = "USUÁRIO REMOVIDO";

    private final UsuarioRepository usuarioRepository;
    private final AlunoRepository alunoRepository;
    private final OrientadorRepository orientadorRepository;
    private final SecretariaRepository secretariaRepository;
    private final CursoRepository cursoRepository;
    private final PreCadastroRepository preCadastroRepository;
    private final ConviteRepository conviteRepository;
    private final LogAuditoriaRepository logAuditoriaRepository;
    private final EstagioRepository estagioRepository;
    private final OrientadorTurmaRepository orientadorTurmaRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;
    private final DoisFatoresService doisFatoresService;
    private final BloqueioContaService bloqueioContaService;

    /*
       Esse é o método que faz a anonimização em si. A ideia não é apagar
       o registro do banco, e sim trocar os dados pessoais por valores
       genéricos, assim o histórico fica preservado para fins estatísticos
       mas ninguém consegue identificar quem era a pessoa. O noRollbackFor
       mantém gravada a tentativa errada, que conta para o bloqueio da conta
    */
    @Transactional(noRollbackFor = {DadoInvalidoException.class, LockedException.class})
    public void anonimizar(String email, String senha, String codigo, String ipOrigem) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        // A regra vem antes da senha, para não gastar tentativas
        impedirSeDeixarOSistemaSemResponsavel(usuario);
        confirmarIdentidade(usuario, senha, codigo, ipOrigem);

        // Registra antes da troca dos dados
        auditoriaService.registrar(usuario, "ANONIMIZACAO_EXECUTADA",
                "Dados pessoais anonimizados por solicitação do titular", ipOrigem, true);

        String emailOriginal = usuario.getEmail();
        String nomeOriginal = usuario.getNome();

        /*
           Gera um sufixo aleatório de 8 caracteres. Ele é usado no e-mail e
           em outros campos para garantir que, se dois usuários forem anonimizados,
           cada um fica com um valor único e não dá conflito de unicidade no banco
        */
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        String emailAnonimizado = "removido_" + sufixo + "@anonimizado.br";

        /*
           Troca os dados principais do usuário. A senha vira um hash de um
           UUID aleatório, ou seja, ninguém mais consegue logar nessa conta
           nem que saiba a senha original. Os tokens, o 2FA e as sessões são encerrados
        */
        usuario.setNome(NOME_ANONIMIZADO);
        usuario.setEmail(emailAnonimizado);
        usuario.setSenhaHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        usuario.setAtivo(false);
        usuario.setConsentimentoDado(false);
        usuario.setTokenRecuperacao(null);
        usuario.setExpiracaoTokenRecuperacao(null);
        usuario.setTokenPreAutenticacao(null);
        usuario.setExpiracaoPreAutenticacao(null);
        usuario.setDoisFatoresAtivo(false);
        usuario.setDoisFatoresSegredo(null);
        usuario.encerrarSessoes();

        /*
           Agora tratamos os campos específicos de cada perfil. Cada tipo de
           usuário tem dados próprios em tabelas separadas, então limpamos
           essas tabelas também, cada uma do seu jeito
        */
        switch (usuario.getPerfil()) {
            case ALUNO -> anonimizarAluno(usuario.getId(), sufixo);
            case ORIENTADOR -> anonimizarOrientador(usuario.getId(), sufixo);
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

        /*
           Apaga o pré-cadastro e troca o e-mail e o nome originais nos convites e na auditoria.
           O flush grava as alterações antes dessas atualizações em lote
        */
        Optional<PreCadastro> preCadastro = preCadastroRepository.findByEmailIgnoreCase(emailOriginal);
        if (preCadastro.isPresent()) {
            preCadastroRepository.delete(preCadastro.get());
        }
        usuarioRepository.flush();
        conviteRepository.trocarEmail(emailOriginal, emailAnonimizado);
        logAuditoriaRepository.trocarEmail(emailOriginal, emailAnonimizado);
        logAuditoriaRepository.trocarTextoNosDetalhes(emailOriginal, emailAnonimizado);
        if (nomeOriginal != null && nomeOriginal.trim().contains(" ")) {
            // Só o nome completo, para não trocar pedaços de outros textos
            logAuditoriaRepository.trocarTextoNosDetalhes(nomeOriginal.trim(), NOME_ANONIMIZADO);
        }

        log.info("[LGPD] Conta id={} anonimizada", usuario.getId());
    }

    // Confere a senha e, com o 2FA ativo, o código. As falhas contam para o bloqueio da conta
    private void confirmarIdentidade(Usuario usuario, String senha, String codigo, String ipOrigem) {
        bloqueioContaService.verificarBloqueio(usuario, "ANONIMIZACAO_FALHA", ipOrigem);

        if (senha == null || !passwordEncoder.matches(senha, usuario.getSenhaHash())) {
            registrarFalha(usuario, "Senha incorreta na confirmação da anonimização", ipOrigem);
            throw new DadoInvalidoException("Senha incorreta.");
        }
        if (Boolean.TRUE.equals(usuario.getDoisFatoresAtivo()) && !doisFatoresService.codigoValido(usuario, codigo)) {
            registrarFalha(usuario, "Código de verificação inválido na confirmação da anonimização", ipOrigem);
            throw new DadoInvalidoException("Código de verificação inválido.");
        }
        bloqueioContaService.limparFalhas(usuario);
    }

    private void registrarFalha(Usuario usuario, String detalhe, String ipOrigem) {
        int tentativas = bloqueioContaService.registrarFalha(usuario);
        auditoriaService.registrar(usuario, "ANONIMIZACAO_FALHA",
                detalhe + ". Tentativa " + tentativas + " de " + BloqueioContaService.MAX_TENTATIVAS, ipOrigem, false);
    }

    // O coordenador com cursos e a última secretaria ativa não podem se anonimizar
    private void impedirSeDeixarOSistemaSemResponsavel(Usuario usuario) {
        if (usuario.getPerfil() == PerfilUsuario.COORDENADOR && cursoRepository.existsByCoordenadorId(usuario.getId())) {
            throw new RegraNegocioException("Você ainda coordena cursos, e eles não podem ficar sem coordenador. "
                    + "Fale com o administrador do sistema antes de anonimizar a conta.");
        }
        if (usuario.getPerfil() == PerfilUsuario.SECRETARIA && secretariaRepository.countByAtivoTrue() <= 1) {
            throw new RegraNegocioException("Você é a única secretaria ativa. Peça ao coordenador para convidar "
                    + "outra secretaria antes de anonimizar a conta.");
        }
    }

    // Aluno: o RGM sai e os estágios em aberto são encerrados por desligamento
    private void anonimizarAluno(Long alunoId, String sufixo) {
        Optional<Aluno> alunoEncontrado = alunoRepository.findById(alunoId);
        if (alunoEncontrado.isPresent()) {
            Aluno aluno = alunoEncontrado.get();
            aluno.setRgm("REMOVIDO_" + sufixo);
            alunoRepository.save(aluno);
        }

        for (Estagio estagio : estagioRepository.findByMatriculaAlunoIdAndStatusIn(alunoId, EstagioService.STATUS_EM_ABERTO)) {
            estagio.setStatus(StatusEstagio.ENCERRADO);
            estagio.setMotivoEncerramento(MotivoEncerramento.DESLIGAMENTO);
            estagioRepository.save(estagio);
        }
    }

    // Orientador: os dados institucionais saem, ele deixa as turmas e os estágios em aberto ficam sem orientador
    private void anonimizarOrientador(Long orientadorId, String sufixo) {
        Optional<Orientador> orientadorEncontrado = orientadorRepository.findById(orientadorId);
        if (orientadorEncontrado.isPresent()) {
            Orientador orientador = orientadorEncontrado.get();
            orientador.setMatriculaInstitucional("REMOVIDO_" + sufixo);
            orientador.setDepartamento("REMOVIDO");
            orientadorRepository.save(orientador);
        }

        orientadorTurmaRepository.deleteAll(orientadorTurmaRepository.findByOrientadorId(orientadorId));
        for (Estagio estagio : estagioRepository.findByOrientadorIdAndStatusIn(orientadorId, EstagioService.STATUS_EM_ABERTO)) {
            estagio.setOrientador(null);
            estagioRepository.save(estagio);
        }
    }

    // Prazo da resposta ao titular nos pedidos de acesso e de correção (art. 19 da LGPD)
    public static final int DIAS_PARA_RESPOSTA = 15;

    // Registra o pedido de acesso ou de correção dos dados (RF43), com protocolo e data limite da resposta
    @Transactional
    public SolicitacaoTitularResponse registrarSolicitacao(String email, String acao, String detalhes, String ipOrigem) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        String protocolo = "LGPD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        LocalDate prazoResposta = LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(DIAS_PARA_RESPOSTA);

        auditoriaService.registrar(usuario, acao,
                detalhes + ". Protocolo " + protocolo + ", resposta até " + prazoResposta, ipOrigem, true);
        return new SolicitacaoTitularResponse(protocolo, prazoResposta);
    }
}
