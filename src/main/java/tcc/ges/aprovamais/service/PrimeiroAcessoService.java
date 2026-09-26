package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.entity.Convite;
import tcc.ges.aprovamais.entity.Usuario;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.UsuarioRepository;

// Esse service é quem fecha o fluxo de primeiro acesso, quando o convidado cria a senha e aceita os termos
@Service
@RequiredArgsConstructor
public class PrimeiroAcessoService {

    private final ConviteService conviteService;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;
    private final ConsentimentoService consentimentoService;

    // Esse é o método que conclui o cadastro quando o usuário define a senha e aceita os termos
    @Transactional
    public void concluirCadastro(String token, String senha, String ip) {
        Convite convite = conviteService.validarToken(token);

        Usuario usuario = usuarioRepository.findByEmail(convite.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        /*
           Aqui acontece a virada de chave do usuário. Ele tava pendente,
           inativo e com senha provisória, agora recebe a senha de verdade
           que ele escolheu, aceita os termos e é ativado. Depois disso já
           consegue logar normalmente
        */
        usuario.setSenhaHash(passwordEncoder.encode(senha));
        consentimentoService.aplicarAceite(usuario);
        usuario.setPrimeiroAcesso(false);
        usuario.setAtivo(true);

        usuarioRepository.save(usuario);

        // Marca o convite como usado, assim o token não vale mais
        conviteService.aceitarConvite(token);

        auditoriaService.registrar(
                usuario,
                "PRIMEIRO_ACESSO_ACEITO",
                "Termos aceitos e cadastro concluído. Versão: " + ConsentimentoService.VERSAO_TERMOS_ATUAL,
                ip,
                true
        );
    }

    // Esse é o método que registra a recusa, quando o convidado decide não criar a conta
    @Transactional
    public void recusarCadastro(String token, String ip) {

        /*
           A validação roda antes de tudo, então se o token tiver expirado
           ou já usado, a recusa nem chega a ser registrada. Isso é de
           propósito, porque não faz sentido marcar como recusado um convite
           que já não era mais válido
        */
        Convite convite = conviteService.validarToken(token);

        /*
           Registra a recusa na auditoria com o e-mail do convite, não com
           um usuário, porque nesse ponto a pessoa ainda não é um usuário
           de fato. Por isso usa o registrarTentativa em vez do registrar
        */
        auditoriaService.registrarTentativa(
                convite.getEmail(),
                "PRIMEIRO_ACESSO_RECUSADO",
                "Usuário recusou os termos de uso",
                ip
        );

        conviteService.recusarConvite(token);
    }
}