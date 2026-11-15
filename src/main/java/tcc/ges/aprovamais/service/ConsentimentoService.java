package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.entity.Usuario;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.UsuarioRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;


// Aceite dos termos de uso e da política de privacidade
@Service
@RequiredArgsConstructor
public class ConsentimentoService {

    /*
       Versão atual dos termos. Ao publicar uma versão nova, basta mudar este valor, e quem aceitou
       uma versão anterior passa de novo pela tela de consentimento
    */
    public static final String VERSAO_TERMOS_ATUAL = "1.1";

    private final UsuarioRepository usuarioRepository;
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

    // Aplica o aceite sem registrar na auditoria, para o primeiro acesso reaproveitar a mesma lógica
    public void aplicarAceite(Usuario usuario) {
        usuario.setConsentimentoDado(true);
        usuario.setDataConsentimento(OffsetDateTime.now(ZoneOffset.UTC));
        usuario.setVersaoConsentimento(VERSAO_TERMOS_ATUAL);
    }
}
