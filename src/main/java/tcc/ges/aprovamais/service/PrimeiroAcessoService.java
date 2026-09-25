package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.entity.Convite;
import tcc.ges.aprovamais.entity.Usuario;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.UsuarioRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class PrimeiroAcessoService {

    private final ConviteService conviteService;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;

    private static final String VERSAO_TERMOS = "1.0";

    @Transactional
    public void concluirCadastro(String token, String senha, String ip) {
        Convite convite = conviteService.validarToken(token);

        Usuario usuario = usuarioRepository.findByEmail(convite.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        usuario.setSenhaHash(passwordEncoder.encode(senha));
        usuario.setConsentimentoDado(true);
        usuario.setDataConsentimento(OffsetDateTime.now(ZoneOffset.UTC));
        usuario.setVersaoConsentimento(VERSAO_TERMOS);
        usuario.setPrimeiroAcesso(false);
        usuario.setAtivo(true);

        usuarioRepository.save(usuario);
        conviteService.aceitarConvite(token);

        auditoriaService.registrar(
                usuario,
                "PRIMEIRO_ACESSO_ACEITO",
                "Termos aceitos e cadastro concluído. Versão: " + VERSAO_TERMOS,
                ip,
                true
        );
    }

    @Transactional
    public void recusarCadastro(String token, String ip) {
        Convite convite = conviteService.validarToken(token);

        auditoriaService.registrarTentativa(
                convite.getEmail(),
                "PRIMEIRO_ACESSO_RECUSADO",
                "Usuário recusou os termos de uso",
                ip
        );

        conviteService.recusarConvite(token);
    }
}