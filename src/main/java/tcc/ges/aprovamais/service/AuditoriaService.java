package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.entity.LogAuditoria;
import tcc.ges.aprovamais.entity.Usuario;
import tcc.ges.aprovamais.entity.enums.ParametroSistema;
import tcc.ges.aprovamais.entity.enums.PerfilAuditoria;
import tcc.ges.aprovamais.entity.enums.PerfilUsuario;
import tcc.ges.aprovamais.repository.LogAuditoriaRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

// Registro dos eventos de auditoria, como login, logout e anonimização
@Service
@RequiredArgsConstructor
public class AuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaService.class);

    private final LogAuditoriaRepository logAuditoriaRepository;

    // Registra um evento na mesma transação da operação, então os dois são gravados ou desfeitos juntos
    @Transactional
    public void registrar(Usuario usuario,
                          String acao,
                          String detalhes,
                          String ipOrigem,
                          boolean sucesso) {
        LogAuditoria registro = new LogAuditoria();
        registro.setUsuario(usuario);
        registro.setEmailTentativa(usuario.getEmail());
        registro.setPerfil(converterPerfil(usuario.getPerfil()));
        registro.setAcao(acao);
        registro.setDetalhes(detalhes);
        registro.setIpOrigem(ipOrigem);
        registro.setSucesso(sucesso);

        logAuditoriaRepository.save(registro);

        log.info("[AUDITORIA] {} | {} | {} | sucesso={}",
                usuario.getEmail(), acao, detalhes, sucesso);
    }

    // Registra um evento sem usuário vinculado, como o login com um e-mail que não existe. Sempre com sucesso false
    @Transactional
    public void registrarTentativa(String emailTentativa,
                                   String acao,
                                   String detalhes,
                                   String ipOrigem) {
        LogAuditoria registro = new LogAuditoria();
        registro.setEmailTentativa(emailTentativa);
        registro.setAcao(acao);
        registro.setDetalhes(detalhes);
        registro.setIpOrigem(ipOrigem);
        registro.setSucesso(false);

        logAuditoriaRepository.save(registro);

        log.warn("[AUDITORIA] {} | {} | {} | sem usuário ativo vinculado", emailTentativa, acao, detalhes);
    }

    // Elimina todo dia, às 3h30, os registros mais antigos que o prazo de guarda
    @Scheduled(cron = "0 30 3 * * *", zone = "America/Sao_Paulo")
    @Transactional
    public void eliminarRegistrosAntigos() {
        OffsetDateTime limite = OffsetDateTime.now(ZoneOffset.UTC).minusMonths(ParametrosVigentes.valor(ParametroSistema.MESES_RETENCAO_AUDITORIA));
        int eliminados = logAuditoriaRepository.apagarAnterioresA(limite);
        if (eliminados > 0) {
            log.info("[AUDITORIA] {} registro(s) anteriores a {} eliminado(s) pelo prazo de guarda", eliminados, limite);
        }
    }

    // Converte o perfil do usuário para o perfil da auditoria, que é um enum separado com os mesmos valores
    private PerfilAuditoria converterPerfil(PerfilUsuario perfil) {
        if (perfil == null) return null;
        return switch (perfil) {
            case ADMINISTRADOR -> PerfilAuditoria.ADMINISTRADOR;
            case COORDENADOR -> PerfilAuditoria.COORDENADOR;
            case SECRETARIA -> PerfilAuditoria.SECRETARIA;
            case ORIENTADOR -> PerfilAuditoria.ORIENTADOR;
            case ALUNO -> PerfilAuditoria.ALUNO;
        };
    }
}