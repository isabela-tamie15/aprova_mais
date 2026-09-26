package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.entity.*;
import tcc.ges.aprovamais.entity.enums.PerfilDestino;
import tcc.ges.aprovamais.entity.enums.PerfilUsuario;
import tcc.ges.aprovamais.entity.enums.StatusConvite;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.AlunoRepository;
import tcc.ges.aprovamais.repository.ConviteRepository;
import tcc.ges.aprovamais.repository.OrientadorRepository;
import tcc.ges.aprovamais.repository.UsuarioRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

// Essa service é quem cuida do ciclo de vida dos convites, desde o envio até o aceite ou recusa
@Service
@RequiredArgsConstructor
public class ConviteService {

    private static final Logger log = LoggerFactory.getLogger(ConviteService.class);

    private final ConviteRepository conviteRepository;
    private final UsuarioRepository usuarioRepository;
    private final EmailService emailService;
    private final AlunoRepository alunoRepository;
    private final OrientadorRepository orientadorRepository;
    private final AuditoriaService auditoriaService;

    // URL base da aplicação, usada pra montar o link do convite que vai no e-mail
    @Value("${app.url}")
    private String appUrl;

    // Esse é o método que envia o convite e cria o usuário pendente se ainda não existir
    @Transactional
    public void enviarConvite(String emailDestino, PerfilDestino perfil,
                              String emailRemetente, Curso curso, Turma turma,
                              String ipOrigem) {

        // Se já tem convite pendente pro mesmo e-mail, não deixa enviar outro
        if (conviteRepository.existsByEmailAndStatus(emailDestino, StatusConvite.PENDENTE)) {
            throw new IllegalStateException(
                    "Já existe um convite pendente para o email: " + emailDestino);
        }

        Usuario remetente = usuarioRepository.findByEmail(emailRemetente)
                .orElseThrow(() -> new ResourceNotFoundException("Remetente não encontrado"));

        /*
           Se ainda não existe usuário com esse e-mail, cria um já com status
           pendente. A ideia é deixar o registro pronto pra quando a pessoa
           aceitar o convite e definir a senha de verdade. Fica ativo=false
           e primeiroAcesso=true até lá
        */
        if (!usuarioRepository.existsByEmail(emailDestino)) {
            if (perfil == PerfilDestino.ALUNO) {
                Aluno aluno = new Aluno();
                aluno.setEmail(emailDestino);
                aluno.setNome("Pendente");
                aluno.setSenhaHash("PENDENTE");
                aluno.setRgm("PENDENTE_" + emailDestino);
                aluno.setPerfil(PerfilUsuario.ALUNO);
                aluno.setAtivo(false);
                aluno.setPrimeiroAcesso(true);
                aluno.setConsentimentoDado(false);
                aluno.setTentativasFalhas(0);
                aluno.setContaBloqueada(false);
                aluno.setDoisFatoresAtivo(false);
                alunoRepository.save(aluno);
            } else {
                // Mesma ideia do aluno, mas pra orientador
                Orientador orientador = new Orientador();
                orientador.setEmail(emailDestino);
                orientador.setNome("Pendente");
                orientador.setSenhaHash("PENDENTE");
                orientador.setMatriculaInstitucional("PENDENTE_" + emailDestino);
                orientador.setPerfil(PerfilUsuario.ORIENTADOR);
                orientador.setAtivo(false);
                orientador.setPrimeiroAcesso(true);
                orientador.setConsentimentoDado(false);
                orientador.setTentativasFalhas(0);
                orientador.setContaBloqueada(false);
                orientador.setDoisFatoresAtivo(false);
                orientadorRepository.save(orientador);
            }
        }

        // Gera o token do convite, que vai no link enviado por e-mail
        String token = UUID.randomUUID().toString();

        Convite convite = new Convite();
        convite.setEmail(emailDestino);
        convite.setTokenConvite(token);
        convite.setPerfilDestino(perfil);
        convite.setRemetente(remetente);
        convite.setCurso(curso);
        convite.setTurma(turma);
        convite.setStatus(StatusConvite.PENDENTE);

        // Convite vale por 24 horas, depois disso precisa pedir outro
        convite.setExpiracaoToken(OffsetDateTime.now(ZoneOffset.UTC).plusHours(24));

        conviteRepository.save(convite);

        String linkConvite = appUrl + "/primeiro-acesso?token=" + token;
        emailService.enviarConvite(emailDestino, emailDestino, linkConvite);

        auditoriaService.registrar(
                remetente,
                "CONVITE_ENVIADO",
                "Convite enviado para: " + emailDestino + " | Perfil: " + perfil,
                ipOrigem,
                true
        );

        log.info("[CONVITE] Convite enviado para: {} pelo remetente: {}",
                emailDestino, emailRemetente);
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
           realmente passou da validade, não que o token tá errado
        */
        if (OffsetDateTime.now(ZoneOffset.UTC).isAfter(convite.getExpiracaoToken())) {
            convite.setStatus(StatusConvite.EXPIRADO);
            conviteRepository.save(convite);
            throw new IllegalStateException("Este convite expirou. Solicite um novo convite à secretaria");
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