package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.dto.ConviteResponse;
import tcc.ges.aprovamais.entity.*;
import tcc.ges.aprovamais.entity.enums.PerfilDestino;
import tcc.ges.aprovamais.entity.enums.PerfilUsuario;
import tcc.ges.aprovamais.entity.enums.StatusConvite;
import tcc.ges.aprovamais.exception.DadoInvalidoException;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.AlunoRepository;
import tcc.ges.aprovamais.repository.ConviteRepository;
import tcc.ges.aprovamais.repository.OrientadorRepository;
import tcc.ges.aprovamais.repository.UsuarioRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// Essa service é quem cuida do ciclo de vida dos convites, desde o envio até o aceite ou recusa
@Service
@RequiredArgsConstructor
public class ConviteService {

    private static final Logger log = LoggerFactory.getLogger(ConviteService.class);

    /*
       Hierarquia de cadastro cada perfil só convida o nível logo abaixo dele.
       Assim um perfil operacional nunca cria uma conta com mais poder que a sua
    */
    private static final Map<PerfilUsuario, Set<PerfilDestino>> HIERARQUIA = Map.of(
            PerfilUsuario.ADMINISTRADOR, Set.of(PerfilDestino.COORDENADOR),
            PerfilUsuario.COORDENADOR, Set.of(PerfilDestino.SECRETARIA),
            PerfilUsuario.SECRETARIA, Set.of(PerfilDestino.ALUNO, PerfilDestino.ORIENTADOR)
    );

    private final ConviteRepository conviteRepository;
    private final UsuarioRepository usuarioRepository;
    private final EmailService emailService;
    private final AlunoRepository alunoRepository;
    private final OrientadorRepository orientadorRepository;
    private final AuditoriaService auditoriaService;
    private final PreCadastroService preCadastroService;

    // URL base da aplicação, usada pra montar o link do convite que vai no e-mail
    @Value("${app.url}")
    private String appUrl;

    // Esse é o método que envia o convite e cria o usuário pendente se ainda não existir
    @Transactional
    public void enviarConvite(String emailDestino, PerfilDestino perfil, String nome,
                              String emailRemetente, String ipOrigem) {

        String email = PreCadastroService.normalizarEmail(emailDestino);

        Usuario remetente = usuarioRepository.findByEmail(emailRemetente)
                .orElseThrow(() -> new ResourceNotFoundException("Remetente não encontrado"));

        // RN01: perfil fora da hierarquia do remetente recebe 403 (CA02)
        if (!HIERARQUIA.getOrDefault(remetente.getPerfil(), Set.of()).contains(perfil)) {
            log.warn("[CONVITE] {} ({}) tentou convidar o perfil {}", emailRemetente, remetente.getPerfil(), perfil);
            throw new AccessDeniedException("Seu perfil não pode convidar " + perfil);
        }

        // RN05: aluno e orientador só recebem convite se estiverem no pré-cadastro, com o mesmo perfil (CA03)
        PreCadastro preCadastro = null;
        if (perfil == PerfilDestino.ALUNO || perfil == PerfilDestino.ORIENTADOR) {
            preCadastro = preCadastroService.buscarPorEmail(email)
                    .orElseThrow(() -> new DadoInvalidoException(
                            "Esta pessoa não está no pré-cadastro. Peça ao coordenador para incluí-la antes do convite"));
            if (preCadastro.getPerfil() != perfil) {
                throw new DadoInvalidoException("No pré-cadastro, esta pessoa está como "
                        + preCadastro.getPerfil() + ", e não como " + perfil);
            }
        }

        // Um convite pendente por email
        if (conviteRepository.existsByEmailAndStatus(email, StatusConvite.PENDENTE)) {
            throw new IllegalStateException("Já existe um convite pendente para o email: " + email);
        }

        /*
           Se ainda não existe usuário com esse email, cria um já com status
           pendente. A ideia é deixar o registro pronto pra quando a pessoa
           aceitar o convite e definir a senha de verdade. Fica ativo=false
           e primeiroAcesso=true até lá. Quem já tem conta ativa não é convidado
           de novo e um cadastro pendente de outro perfil também barra o envio
        */
        Optional<Usuario> existente = usuarioRepository.findByEmail(email);
        if (existente.isPresent()) {
            Usuario usuario = existente.get();
            if (!Boolean.TRUE.equals(usuario.getPrimeiroAcesso())) {
                throw new IllegalStateException("Já existe uma conta ativa com este e-mail");
            }
            if (!usuario.getPerfil().name().equals(perfil.name())) {
                throw new IllegalStateException("Já existe um cadastro pendente com outro perfil para este e-mail");
            }
        } else {
            String nomeConvidado = preCadastro != null
                    ? preCadastro.getNome()
                    : (nome != null && !nome.isBlank() ? nome.trim() : email);
            criarUsuarioPendente(email, perfil, nomeConvidado);
        }

        // Gera o token do convite, que vai no link enviado por e-mail
        String token = UUID.randomUUID().toString();

        Convite convite = new Convite();
        convite.setEmail(email);
        convite.setTokenConvite(token);
        convite.setPerfilDestino(perfil);
        convite.setRemetente(remetente);

        // Para aluno, o convite já leva a turma e o curso do pré-cadastro, onde a matrícula será criada
        if (preCadastro != null && preCadastro.getTurma() != null) {
            convite.setTurma(preCadastro.getTurma());
            convite.setCurso(preCadastro.getTurma().getCurso());
        }
        convite.setStatus(StatusConvite.PENDENTE);

        // Convite vale por 24 horas, depois disso precisa pedir outro
        convite.setExpiracaoToken(OffsetDateTime.now(ZoneOffset.UTC).plusHours(24));

        conviteRepository.save(convite);

        String linkConvite = appUrl + "/primeiro-acesso?token=" + token;
        emailService.enviarConvite(email, email, linkConvite);

        auditoriaService.registrar(
                remetente,
                "CONVITE_ENVIADO",
                "Convite enviado para: " + email + " | Perfil: " + perfil,
                ipOrigem,
                true
        );

        log.info("[CONVITE] Convite enviado para: {} pelo remetente: {}", email, emailRemetente);
    }

    // Esse é o método que lista os convites enviados pelo usuário logado, do mais recente para o mais antigo
    @Transactional(readOnly = true)
    public List<ConviteResponse> listarEnviados(String emailRemetente) {
        return conviteRepository.findByRemetenteEmailOrderByCriadoEmDesc(emailRemetente)
                .stream()
                .map(convite -> ConviteResponse.builder()
                        .id(convite.getId())
                        .email(convite.getEmail())
                        .perfilDestino(convite.getPerfilDestino().name())
                        .status(convite.getStatus().name())
                        .criadoEm(convite.getCriadoEm())
                        .expiracaoToken(convite.getExpiracaoToken())
                        .build())
                .toList();
    }

    /*
       Cria a conta pendente do convidado, com senha provisória que não serve para
       login. O RGM e a matrícula institucional reais vêm do pré-cadastro no
       aceite
    */
    private void criarUsuarioPendente(String email, PerfilDestino perfil, String nome) {
        Usuario usuario = switch (perfil) {
            case ALUNO -> {
                Aluno aluno = new Aluno();
                aluno.setRgm("PENDENTE_" + email);
                aluno.setPerfil(PerfilUsuario.ALUNO);
                yield aluno;
            }
            case ORIENTADOR -> {
                Orientador orientador = new Orientador();
                orientador.setMatriculaInstitucional("PENDENTE_" + email);
                orientador.setPerfil(PerfilUsuario.ORIENTADOR);
                yield orientador;
            }
            case SECRETARIA -> {
                Secretaria secretaria = new Secretaria();
                secretaria.setSetor("Secretaria Acadêmica");
                secretaria.setPerfil(PerfilUsuario.SECRETARIA);
                yield secretaria;
            }
            case COORDENADOR -> {
                Coordenador coordenador = new Coordenador();
                coordenador.setPerfil(PerfilUsuario.COORDENADOR);
                yield coordenador;
            }
        };
        usuario.setEmail(email);
        usuario.setNome(nome);
        usuario.setSenhaHash("PENDENTE");
        usuario.setAtivo(false);
        usuario.setPrimeiroAcesso(true);
        usuario.setConsentimentoDado(false);
        usuario.setTentativasFalhas(0);
        usuario.setContaBloqueada(false);
        usuario.setDoisFatoresAtivo(false);

        if (usuario instanceof Aluno aluno) {
            alunoRepository.save(aluno);
        } else if (usuario instanceof Orientador orientador) {
            orientadorRepository.save(orientador);
        } else {
            usuarioRepository.save(usuario);
        }
    }

    // Esse é o método que valida um token de convite, usado antes de aceitar, recusar ou abrir a página
    @Transactional(readOnly = true)
    public Convite validarToken(String token) {
        Convite convite = conviteRepository.findByTokenConvite(token)
                .orElseThrow(() -> new ResourceNotFoundException("Convite não encontrado ou inválido"));

        // Se já foi usado ou cancelado, não vale mais
        if (convite.getStatus() != StatusConvite.PENDENTE) {
            throw new IllegalStateException("Este convite já foi utilizado ou cancelado");
        }

        /*
           Se o prazo já venceu, marca como expirado no banco antes de lançar.
           Assim o registro fica atualizado e a pessoa sabe que o convite
           realmente passou da validade, não que o token tá errado.
           A transação é só leitura e a exceção logo abaixo desfaz tudo, então o update roda numa transação própria
        */
        if (OffsetDateTime.now(ZoneOffset.UTC).isAfter(convite.getExpiracaoToken())) {
            convite.setStatus(StatusConvite.EXPIRADO);
            conviteRepository.atualizarStatus(convite.getId(), StatusConvite.EXPIRADO);
            throw new IllegalStateException("Este convite expirou. Solicite um novo convite a quem enviou");
        }

        return convite;
    }

    // Marca o convite como aceito, usado quando o usuário conclui o cadastro pelo primeiro acesso
    @Transactional
    public void aceitarConvite(String token) {
        Convite convite = validarToken(token);
        convite.setStatus(StatusConvite.ACEITO);
        conviteRepository.save(convite);
        log.info("[CONVITE] Convite aceito para: {}", convite.getEmail());
    }

    // Marca o convite como recusado, usado quando o usuário clica em recusar na tela de primeiro acesso
    @Transactional
    public void recusarConvite(String token) {
        Convite convite = validarToken(token);
        convite.setStatus(StatusConvite.RECUSADO);
        conviteRepository.save(convite);
        log.info("[CONVITE] Convite recusado para: {}", convite.getEmail());
    }
}