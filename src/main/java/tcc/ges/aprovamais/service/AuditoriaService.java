package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tcc.ges.aprovamais.entity.LogAuditoria;
import tcc.ges.aprovamais.entity.Usuario;
import tcc.ges.aprovamais.entity.enums.PerfilAuditoria;
import tcc.ges.aprovamais.entity.enums.PerfilUsuario;
import tcc.ges.aprovamais.repository.LogAuditoriaRepository;

@Service
@RequiredArgsConstructor
public class AuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaService.class);

    private final LogAuditoriaRepository logAuditoriaRepository;

    @Async
    public void registrar(Usuario usuario,
                          String acao,
                          String detalhes,
                          String ipOrigem,
                          boolean sucesso) {
        try {
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

        } catch (Exception e) {
            log.error("[AUDITORIA] Erro ao registrar auditoria: {}", e.getMessage(), e);
        }
    }

    @Async
    public void registrarTentativa(String emailTentativa,
                                   String acao,
                                   String detalhes,
                                   String ipOrigem) {
        try {
            LogAuditoria registro = new LogAuditoria();
            registro.setEmailTentativa(emailTentativa);
            registro.setAcao(acao);
            registro.setDetalhes(detalhes);
            registro.setIpOrigem(ipOrigem);
            registro.setSucesso(false);

            logAuditoriaRepository.save(registro);

            log.warn("[AUDITORIA] Tentativa com email inexistente: {} | {}", emailTentativa, acao);

        } catch (Exception e) {
            log.error("[AUDITORIA] Erro ao registrar auditoria: {}", e.getMessage(), e);
        }
    }

    private PerfilAuditoria converterPerfil(PerfilUsuario perfil) {
        if (perfil == null) return null;
        return switch (perfil) {
            case COORDENADOR -> PerfilAuditoria.COORDENADOR;
            case SECRETARIA -> PerfilAuditoria.SECRETARIA;
            case ORIENTADOR -> PerfilAuditoria.ORIENTADOR;
            case ALUNO -> PerfilAuditoria.ALUNO;
        };
    }
}