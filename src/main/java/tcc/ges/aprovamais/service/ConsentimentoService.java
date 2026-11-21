package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.entity.AceiteTermos;
import tcc.ges.aprovamais.entity.Usuario;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.AceiteTermosRepository;
import tcc.ges.aprovamais.repository.UsuarioRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;


// Aceite dos termos de uso e da política de privacidade
@Service
@RequiredArgsConstructor
public class ConsentimentoService {


    public static final String VERSAO_TERMOS_ATUAL = "1.2";
    public static final String VIGENCIA_TERMOS_ATUAL = "19/11/2026";

    private final UsuarioRepository usuarioRepository;
    private final AceiteTermosRepository aceiteTermosRepository;
    private final AuditoriaService auditoriaService;

    // Registra o aceite do usuário logado na auditoria, com o IP
    @Transactional
    public void registrarAceite(String email, String ipOrigem) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        aplicarAceite(usuario);
        usuarioRepository.save(usuario);

        auditoriaService.registrar(
                usuario,
                "CONSENTIMENTO_ACEITO",
                "Usuário aceitou os termos de uso e política de privacidade. Versão: " + VERSAO_TERMOS_ATUAL,
                ipOrigem,
                true
        );
    }

    // Diz se o usuário ainda precisa aceitar os termos. Quem não existe no banco não é bloqueado aqui
    @Transactional(readOnly = true)
    public boolean precisaConsentir(String email) {
        Optional<Usuario> usuarioDoEmail = usuarioRepository.findByEmail(email);
        if (usuarioDoEmail.isEmpty()) {
            return false;
        }

        return precisaConsentir(usuarioDoEmail.get());
    }

    // Precisa aceitar quem nunca aceitou ou quem aceitou uma versão antiga dos documentos
    public static boolean precisaConsentir(Usuario usuario) {
        boolean nuncaAceitou = !Boolean.TRUE.equals(usuario.getConsentimentoDado());
        boolean aceitouVersaoAntiga = !VERSAO_TERMOS_ATUAL.equals(usuario.getVersaoConsentimento());
        return nuncaAceitou || aceitouVersaoAntiga;
    }

    /*
       Aplica o aceite no usuário e grava uma linha no histórico de aceites. Não registra na auditoria,
       para o primeiro acesso reaproveitar a mesma lógica com o seu próprio registro
    */
    public void aplicarAceite(Usuario usuario) {
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        usuario.setConsentimentoDado(true);
        usuario.setDataConsentimento(agora);
        usuario.setVersaoConsentimento(VERSAO_TERMOS_ATUAL);
        aceiteTermosRepository.save(new AceiteTermos(usuario, VERSAO_TERMOS_ATUAL, agora));
    }

    /*
       Quem aceitou uma versão anterior à criação do histórico ainda não tem linha nele.
       Na subida do sistema, o último aceite guardado no usuário é copiado para o histórico, uma vez só
    */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void registrarAceitesAnteriores() {
        for (Usuario usuario : usuarioRepository.findAll()) {
            boolean temAceite = Boolean.TRUE.equals(usuario.getConsentimentoDado())
                    && usuario.getVersaoConsentimento() != null
                    && usuario.getDataConsentimento() != null;
            if (temAceite && !aceiteTermosRepository.existsByUsuarioId(usuario.getId())) {
                aceiteTermosRepository.save(new AceiteTermos(
                        usuario, usuario.getVersaoConsentimento(), usuario.getDataConsentimento()));
            }
        }
    }
}
