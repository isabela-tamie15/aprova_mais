package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.entity.LogAuditoria;
import tcc.ges.aprovamais.entity.Usuario;
import tcc.ges.aprovamais.entity.enums.PerfilAuditoria;
import tcc.ges.aprovamais.entity.enums.PerfilUsuario;
import tcc.ges.aprovamais.repository.LogAuditoriaRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

// Essa service é quem registra os logs de auditoria do sistema, tipo login, logout, anonimização etc
@Service
@RequiredArgsConstructor
public class AuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaService.class);

    private final LogAuditoriaRepository logAuditoriaRepository;

    // Prazo de guarda dos registros, em meses (RF45)
    @Value("${app.auditoria.retencao-meses:6}")
    private int mesesDeRetencao;

    // Registra um evento de auditoria na mesma transação da operação: os dois são gravados ou desfeitos juntos
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

    /*
       Versão usada quando não tem usuário vinculado, tipo tentativa de login
       com e-mail que nem existe no banco. O sucesso fica fixo em false
       porque, por definição, esses são eventos de falha
    */
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

    // Elimina todo dia, às 3h30, os registros mais antigos que o prazo de guarda (RF45)
    @Scheduled(cron = "0 30 3 * * *", zone = "America/Sao_Paulo")
    @Transactional
    public void eliminarRegistrosAntigos() {
        OffsetDateTime limite = OffsetDateTime.now(ZoneOffset.UTC).minusMonths(mesesDeRetencao);
        int eliminados = logAuditoriaRepository.apagarAnterioresA(limite);
        if (eliminados > 0) {
            log.info("[AUDITORIA] {} registro(s) anteriores a {} eliminado(s) pelo prazo de guarda", eliminados, limite);
        }
    }

    /*
       Converte do enum de perfil usado na entidade para o enum da auditoria.
       São dois enums separados com os mesmos valores, mas cada um fica na
       sua camada, então precisa dessa conversão no meio
    */
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