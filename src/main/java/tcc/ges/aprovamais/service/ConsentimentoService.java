package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.entity.Usuario;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.UsuarioRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;


// Essa service é quem cuida do aceite dos termos de uso e da política de privacidade
@Service
@RequiredArgsConstructor
public class ConsentimentoService {

    // Ao publicar uma nova versão dos termos, atualizar apenas este valor.
    public static final String VERSAO_TERMOS_ATUAL = "1.0";

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
       Método separado porque o aceite também acontece no fluxo de primeiro
       acesso, quando o usuário cria a conta, e lá não tem IP nem auditoria
       pra registrar. Assim os dois fluxos reaproveitam a mesma lógica
    */
    public void aplicarAceite(Usuario usuario) {
        usuario.setConsentimentoDado(true);
        usuario.setDataConsentimento(OffsetDateTime.now(ZoneOffset.UTC));
        usuario.setVersaoConsentimento(VERSAO_TERMOS_ATUAL);
    }
}
