package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.entity.Convite;
import tcc.ges.aprovamais.entity.Curso;
import tcc.ges.aprovamais.entity.Turma;
import tcc.ges.aprovamais.entity.Usuario;
import tcc.ges.aprovamais.entity.enums.PerfilDestino;
import tcc.ges.aprovamais.entity.enums.StatusConvite;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.ConviteRepository;
import tcc.ges.aprovamais.repository.UsuarioRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConviteService {

    private static final Logger log = LoggerFactory.getLogger(ConviteService.class);
    private static final String VERSAO_TERMOS = "1.0";

    private final ConviteRepository conviteRepository;
    private final UsuarioRepository usuarioRepository;
    private final EmailService emailService;

    @Value("${app.url}")
    private String appUrl;

    @Transactional
    public void enviarConvite(String emailDestino, PerfilDestino perfil,
                              String emailRemetente, Curso curso, Turma turma) {

        if (conviteRepository.existsByEmailAndStatus(emailDestino, StatusConvite.PENDENTE)) {
            throw new IllegalStateException(
                    "Já existe um convite pendente para o email: " + emailDestino);
        }

        Usuario remetente = usuarioRepository.findByEmail(emailRemetente)
                .orElseThrow(() -> new ResourceNotFoundException("Remetente não encontrado"));

        String token = UUID.randomUUID().toString();

        Convite convite = new Convite();
        convite.setEmail(emailDestino);
        convite.setTokenConvite(token);
        convite.setPerfilDestino(perfil);
        convite.setRemetente(remetente);
        convite.setCurso(curso);
        convite.setTurma(turma);
        convite.setStatus(StatusConvite.PENDENTE);
        convite.setExpiracaoToken(OffsetDateTime.now(ZoneOffset.UTC).plusHours(24));

        conviteRepository.save(convite);

        String linkConvite = appUrl + "/primeiro-acesso?token=" + token;

        emailService.enviarConvite(emailDestino, emailDestino, linkConvite);

        log.info("[CONVITE] Convite enviado para: {} pelo remetente: {}",
                emailDestino, emailRemetente);
    }

    @Transactional(readOnly = true)
    public Convite validarToken(String token) {
        Convite convite = conviteRepository.findByTokenConvite(token)
                .orElseThrow(() -> new ResourceNotFoundException("Convite não encontrado ou inválido"));

        if (convite.getStatus() != StatusConvite.PENDENTE) {
            throw new IllegalStateException("Este convite já foi utilizado ou cancelado");
        }

        if (OffsetDateTime.now(ZoneOffset.UTC).isAfter(convite.getExpiracaoToken())) {
            convite.setStatus(StatusConvite.EXPIRADO);
            conviteRepository.save(convite);
            throw new IllegalStateException("Este convite expirou. Solicite um novo convite à secretaria");
        }

        return convite;
    }

    @Transactional
    public void aceitarConvite(String token) {
        Convite convite = validarToken(token);
        convite.setStatus(StatusConvite.ACEITO);
        conviteRepository.save(convite);
        log.info("[CONVITE] Convite aceito para: {}", convite.getEmail());
    }

    @Transactional
    public void recusarConvite(String token) {
        Convite convite = validarToken(token);
        convite.setStatus(StatusConvite.RECUSADO);
        conviteRepository.save(convite);
        log.info("[CONVITE] Convite recusado para: {}", convite.getEmail());
    }
}