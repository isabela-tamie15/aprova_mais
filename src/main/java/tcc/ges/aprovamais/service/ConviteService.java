package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.dto.ConviteResponse;
import tcc.ges.aprovamais.entity.*;
import tcc.ges.aprovamais.entity.enums.ParametroSistema;
import tcc.ges.aprovamais.entity.enums.PerfilDestino;
import tcc.ges.aprovamais.entity.enums.PerfilUsuario;
import tcc.ges.aprovamais.entity.enums.StatusConvite;
import tcc.ges.aprovamais.exception.DadoInvalidoException;
import tcc.ges.aprovamais.exception.RegraNegocioException;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.external.EmailService;
import tcc.ges.aprovamais.repository.AlunoRepository;
import tcc.ges.aprovamais.repository.ConviteRepository;
import tcc.ges.aprovamais.repository.OrientadorRepository;
import tcc.ges.aprovamais.repository.UsuarioRepository;
import tcc.ges.aprovamais.security.HashToken;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// Envio, validação, aceite e recusa dos convites
@Service
@RequiredArgsConstructor
public class ConviteService {

    private static final Logger log = LoggerFactory.getLogger(ConviteService.class);

    // Cada perfil só convida o nível logo abaixo dele, então ninguém cria uma conta com mais poder que a sua
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

    // URL base da aplicação, usada para montar o link do convite que vai no e-mail
    @Value("${app.url}")
    private String appUrl;

    // Envia o convite e cria o usuário pendente quando ele ainda não existe
    @Transactional
    public void enviarConvite(String emailDestino, PerfilDestino perfil, String nome,
                              String emailRemetente, String ipOrigem) {

        String email = PreCadastroService.normalizarEmail(emailDestino);

        Usuario remetente = usuarioRepository.findByEmail(emailRemetente)
                .orElseThrow(() -> new ResourceNotFoundException("Remetente não encontrado"));

        // Perfil fora da hierarquia do remetente recebe 403
        if (!HIERARQUIA.getOrDefault(remetente.getPerfil(), Set.of()).contains(perfil)) {
            log.warn("[CONVITE] {} ({}) tentou convidar o perfil {}", emailRemetente, remetente.getPerfil(), perfil);
            throw new AccessDeniedException("Seu perfil não pode convidar " + perfil);
        }

        // Aluno e orientador só recebem convite se estiverem no pré-cadastro com o mesmo perfil
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

        /*
           Um convite pendente por e-mail. O convite vencido desse e-mail vira EXPIRADO antes da conferência,
           para a secretaria poder mandar um novo depois do vencimento
        */
        conviteRepository.expirarVencidosDoEmail(email, OffsetDateTime.now(ZoneOffset.UTC));
        if (conviteRepository.existsByEmailAndStatus(email, StatusConvite.PENDENTE)) {
            throw new RegraNegocioException("Já existe um convite pendente para o email: " + email);
        }

        /*
           Sem usuário com esse e-mail, cria um pendente, inativo e com primeiroAcesso true, pronto para o
           aceite do convite. Quem já tem conta ativa não é convidado de novo, e um cadastro pendente
           de outro perfil também barra o envio
        */
        Optional<Usuario> existente = usuarioRepository.findByEmail(email);
        if (existente.isPresent()) {
            Usuario usuario = existente.get();
            if (!Boolean.TRUE.equals(usuario.getPrimeiroAcesso())) {
                throw new RegraNegocioException("Já existe uma conta ativa com este e-mail");
            }
            if (!usuario.getPerfil().name().equals(perfil.name())) {
                throw new RegraNegocioException("Já existe um cadastro pendente com outro perfil para este e-mail");
            }
        } else {
            String nomeConvidado = preCadastro != null
                    ? preCadastro.getNome()
                    : (nome != null && !nome.isBlank() ? nome.trim() : email);
            criarUsuarioPendente(email, perfil, nomeConvidado);
        }

        // Token do convite, que vai no link do e-mail. O banco guarda só o hash dele
        String token = UUID.randomUUID().toString();

        Convite convite = new Convite();
        convite.setEmail(email);
        convite.setTokenConvite(HashToken.sha256(token));
        convite.setPerfilDestino(perfil);
        convite.setRemetente(remetente);

        // Para aluno, o convite já leva a turma e o curso do pré-cadastro, onde a matrícula será criada
        if (preCadastro != null && preCadastro.getTurma() != null) {
            convite.setTurma(preCadastro.getTurma());
            convite.setCurso(preCadastro.getTurma().getCurso());
        }
        convite.setStatus(StatusConvite.PENDENTE);

        // O convite vale pelas horas configuradas, 24 por padrão. Depois disso precisa de outro
        convite.setExpiracaoToken(OffsetDateTime.now(ZoneOffset.UTC)
                .plusHours(ParametrosVigentes.valor(ParametroSistema.HORAS_VALIDADE_CONVITE)));

        conviteRepository.save(convite);

        String linkConvite = appUrl + "/primeiro-acesso?token=" + token;
        emailService.enviarConvite(email, email, linkConvite);

        // Só aparece com o log em DEBUG, que é o do perfil dev
        log.debug("[CONVITE] Link de primeiro acesso para {}: {}", email, linkConvite);

        auditoriaService.registrar(
                remetente,
                "CONVITE_ENVIADO",
                "Convite enviado para: " + email + " | Perfil: " + perfil,
                ipOrigem,
                true
        );

        log.info("[CONVITE] Convite enviado para: {} pelo remetente: {}", email, emailRemetente);
    }

    // Lista os convites enviados pelo usuário logado, do mais recente para o mais antigo
    @Transactional(readOnly = true)
    public List<ConviteResponse> listarEnviados(String emailRemetente) {
        List<Convite> convitesEnviados = conviteRepository.findByRemetenteEmailOrderByCriadoEmDesc(emailRemetente);

        // Transforma cada convite do banco na resposta que vai para a tela
        List<ConviteResponse> respostas = new ArrayList<>();
        for (Convite convite : convitesEnviados) {
            respostas.add(ConviteResponse.builder()
                    .id(convite.getId())
                    .email(convite.getEmail())
                    .perfilDestino(convite.getPerfilDestino().name())
                    .status(convite.getStatus().name())
                    .criadoEm(convite.getCriadoEm())
                    .expiracaoToken(convite.getExpiracaoToken())
                    .build());
        }
        return respostas;
    }

    /*
       Cria a conta pendente do convidado, com uma senha provisória que não serve para login.
       O RGM e a matrícula institucional reais vêm do pré-cadastro no aceite
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

    // Marca todo dia, às 3h, os convites vencidos como EXPIRADO, para a lista de convites mostrar a situação real
    @Scheduled(cron = "0 0 3 * * *", zone = "America/Sao_Paulo")
    @Transactional
    public void expirarConvitesVencidos() {
        int expirados = conviteRepository.expirarVencidos(OffsetDateTime.now(ZoneOffset.UTC));
        if (expirados > 0) {
            log.info("[CONVITE] {} convite(s) vencido(s) marcado(s) como expirado(s)", expirados);
        }
    }

    // Valida o token do convite, antes de abrir a página, aceitar ou recusar
    @Transactional(readOnly = true)
    public Convite validarToken(String token) {
        if (token == null || token.isBlank() || token.length() > 200) {
            throw new ResourceNotFoundException("Convite não encontrado ou inválido");
        }
        Convite convite = conviteRepository.findByTokenConvite(HashToken.sha256(token))
                .orElseThrow(() -> new ResourceNotFoundException("Convite não encontrado ou inválido"));

        // Se já foi usado ou cancelado, não vale mais
        if (convite.getStatus() != StatusConvite.PENDENTE) {
            throw new RegraNegocioException("Este convite já foi utilizado ou cancelado");
        }

        /*
           Convite vencido é marcado como expirado antes da exceção, para a pessoa saber que ele passou
           da validade. Como a exceção desfaz a transação de leitura, o update roda numa transação própria
        */
        if (OffsetDateTime.now(ZoneOffset.UTC).isAfter(convite.getExpiracaoToken())) {
            convite.setStatus(StatusConvite.EXPIRADO);
            conviteRepository.atualizarStatus(convite.getId(), StatusConvite.EXPIRADO);
            throw new RegraNegocioException("Este convite expirou. Solicite um novo convite a quem enviou");
        }

        return convite;
    }

    /*
       Soma uma tentativa com RGM errado no convite. Ao chegar no limite, o convite é cancelado e a
       secretaria precisa enviar outro. Devolve o total de tentativas erradas
    */
    @Transactional
    public int registrarRgmIncorreto(Convite convite, int limite) {
        int tentativas = (convite.getTentativasRgm() == null ? 0 : convite.getTentativasRgm()) + 1;
        convite.setTentativasRgm(tentativas);
        if (tentativas >= limite) {
            convite.setStatus(StatusConvite.CANCELADO);
            log.warn("[CONVITE] Convite para {} cancelado após {} tentativas com RGM errado", convite.getEmail(), tentativas);
        }
        conviteRepository.save(convite);
        return tentativas;
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