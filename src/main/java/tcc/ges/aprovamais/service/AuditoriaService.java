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

// Essa service é quem registra os logs de auditoria do sistema, tipo login, logout, anonimização etc
@Service
@RequiredArgsConstructor
public class AuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaService.class);

    private final LogAuditoriaRepository logAuditoriaRepository;

    /*
       Registra um evento de auditoria vinculado a um usuário. O @Async faz
       esse método rodar em outra thread, então quem chamou não fica esperando
       ele terminar. Se a auditoria falhar, o fluxo principal não quebra,
       por isso o try/catch engole a exceção e só loga o erro
    */
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

    /*
       Versão usada quando não tem usuário vinculado, tipo tentativa de login
       com e-mail que nem existe no banco. O sucesso fica fixo em false
       porque, por definição, esses são eventos de falha
    */
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

            log.warn("[AUDITORIA] {} | {} | {} | sem usuário ativo vinculado", emailTentativa, acao, detalhes);

        } catch (Exception e) {
            log.error("[AUDITORIA] Erro ao registrar auditoria: {}", e.getMessage(), e);
        }
    }

    /*
       Converte do enum de perfil usado na entidade pro enum da auditoria.
       São dois enums separados com os mesmos valores, mas cada um fica na
       sua camada, então precisa dessa conversão no meio
    */
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