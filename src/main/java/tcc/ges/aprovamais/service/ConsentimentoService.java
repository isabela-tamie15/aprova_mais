package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.entity.Usuario;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.UsuarioRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;


@Service
@RequiredArgsConstructor
public class ConsentimentoService {


    // Ao publicar uma nova versão dos termos, atualizar apenas este valor.
    public static final String VERSAO_TERMOS_ATUAL = "1.0";

    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;


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


    public void aplicarAceite(Usuario usuario) {
        usuario.setConsentimentoDado(true);
        usuario.setDataConsentimento(OffsetDateTime.now(ZoneOffset.UTC));
        usuario.setVersaoConsentimento(VERSAO_TERMOS_ATUAL);
    }
}
