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


// Essa service é quem cuida do aceite dos termos de uso e da política de privacidade
@Service
@RequiredArgsConstructor
public class ConsentimentoService {

    /*
       Ao publicar uma nova versão dos termos, nós atualizamos apenas este valor.
       A 1.1 (novembro de 2026) incluiu o administrador, o Cloudflare R2, a BrasilAPI,
       o jsDelivr e os e-mails de recuperação de senha e de resultado das entregas.
       Quem aceitou a 1.0 passa de novo pela tela de consentimento (RN60)
    */
    public static final String VERSAO_TERMOS_ATUAL = "1.1";

    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;

    // Esse é o método que registra o aceite do usuário logado e guarda na auditoria com o IP
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

    /*
       Diz se o usuário ainda precisa aceitar os termos, se ele nunca aceitou, ou
       aceitou uma versão antiga.
       Quem não existe no banco não é bloqueado aqui
    */
    @Transactional(readOnly = true)
    public boolean precisaConsentir(String email) {
        Optional<Usuario> usuarioDoEmail = usuarioRepository.findByEmail(email);
        if (usuarioDoEmail.isEmpty()) {
            return false;
        }

        return precisaConsentir(usuarioDoEmail.get());
    }

    // Precisa aceitar quem nunca aceitou ou quem aceitou uma versão antiga dos documentos (RN60)
    public static boolean precisaConsentir(Usuario usuario) {
        boolean nuncaAceitou = !Boolean.TRUE.equals(usuario.getConsentimentoDado());
        boolean aceitouVersaoAntiga = !VERSAO_TERMOS_ATUAL.equals(usuario.getVersaoConsentimento());
        return nuncaAceitou || aceitouVersaoAntiga;
    }

    /*
       Método separado porque o aceite também acontece no fluxo de primeiro
       acesso, quando o usuário cria a conta, e lá não tem IP nem auditoria
       para registrar. Assim os dois fluxos reaproveitam a mesma lógica
    */
    public void aplicarAceite(Usuario usuario) {
        usuario.setConsentimentoDado(true);
        usuario.setDataConsentimento(OffsetDateTime.now(ZoneOffset.UTC));
        usuario.setVersaoConsentimento(VERSAO_TERMOS_ATUAL);
    }
}
