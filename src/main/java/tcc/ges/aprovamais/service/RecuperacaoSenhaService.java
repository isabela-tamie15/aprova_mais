package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.security.HashToken;
import tcc.ges.aprovamais.entity.Usuario;
import tcc.ges.aprovamais.exception.DadoInvalidoException;
import tcc.ges.aprovamais.external.EmailService;
import tcc.ges.aprovamais.repository.UsuarioRepository;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;

/*
   Esse service é quem cuida da recuperação de senha (RF09). O fluxo tem duas partes:
   o pedido, que manda um link por e-mail, e a redefinição, que troca a senha.
   O token do link é aleatório, vale por pouco tempo (30 minutos por padrão) e uma
   única vez, e o banco só guarda o hash dele, igual ao token de pré-autenticação
   do 2FA. Assim, quem ler o banco não consegue montar um link válido
*/
@Service
@RequiredArgsConstructor
public class RecuperacaoSenhaService {

    private static final Logger log = LoggerFactory.getLogger(RecuperacaoSenhaService.class);
    private static final SecureRandom GERADOR = new SecureRandom();

    // Intervalo mínimo entre dois e-mails para a mesma conta, para ninguém usar a tela para encher a caixa de alguém
    private static final long INTERVALO_MINIMO_MINUTOS = 2;

    public static final String MENSAGEM_LINK_INVALIDO =
            "Este link de recuperação é inválido ou expirou. Peça um novo na tela de login.";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuditoriaService auditoriaService;
    private final BloqueioContaService bloqueioContaService;

    @Value("${app.url}")
    private String appUrl;

    @Value("${aprova.recuperacao-senha.validade-minutos:30}")
    private long validadeMinutos;

    /*
       Esse é o método do pedido de recuperação. Ele não devolve nada de propósito:
       a tela mostra sempre a mesma mensagem, exista ou não a conta (RN10), senão
       daria para descobrir quais e-mails têm cadastro. Só conta ativa e que já
       concluiu o primeiro acesso recebe o link; quem ainda tem convite pendente
       usa o link do convite
    */
    @Transactional
    public void solicitar(String email, String ipOrigem) {
        String emailNormalizado = email.trim();
        Optional<Usuario> usuarioDoEmail = usuarioRepository.findByEmail(emailNormalizado);

        // Só recebe o link quem tem conta ativa e já fez o primeiro acesso
        boolean contaExiste = usuarioDoEmail.isPresent();
        boolean contaAtiva = contaExiste && Boolean.TRUE.equals(usuarioDoEmail.get().getAtivo());
        boolean jaFezPrimeiroAcesso = contaExiste && !Boolean.TRUE.equals(usuarioDoEmail.get().getPrimeiroAcesso());

        if (!contaAtiva || !jaFezPrimeiroAcesso) {
            // A resposta para a tela é a mesma nos dois casos: não revelamos se o e-mail tem conta
            auditoriaService.registrarTentativa(emailNormalizado, "RECUPERACAO_SENHA_SOLICITADA",
                    "Pedido de recuperação para e-mail sem conta ativa", ipOrigem);
            return;
        }

        Usuario usuario = usuarioDoEmail.get();
        if (pedidoRecente(usuario)) {
            log.info("[RECUPERAÇÃO] Pedido repetido em menos de {} minutos, e-mail não reenviado", INTERVALO_MINIMO_MINUTOS);
            return;
        }

        String token = gerarToken();
        usuario.setTokenRecuperacao(HashToken.sha256(token));
        usuario.setExpiracaoTokenRecuperacao(agora().plusMinutes(validadeMinutos));
        usuarioRepository.save(usuario);

        emailService.enviarRecuperacaoSenha(usuario.getEmail(), usuario.getNome(),
                appUrl + "/redefinir-senha?token=" + token);
        auditoriaService.registrar(usuario, "RECUPERACAO_SENHA_SOLICITADA",
                "Link de recuperação enviado por e-mail", ipOrigem, true);
    }

    // Usado pela página do link, para já mostrar o aviso se o link não vale mais
    @Transactional(readOnly = true)
    public boolean tokenValido(String token) {
        return buscarPorTokenValido(token).isPresent();
    }

    /*
       Esse é o método que troca a senha. Depois disso o token é apagado, então o
       mesmo link não funciona de novo (CA02). Também zera as tentativas de login
       erradas, porque quem abriu o link provou que tem acesso ao e-mail
    */
    @Transactional
    public void redefinir(String token, String novaSenha, String ipOrigem) {
        Usuario usuario = buscarPorTokenValido(token)
                .orElseThrow(() -> new DadoInvalidoException(MENSAGEM_LINK_INVALIDO));

        usuario.setSenhaHash(passwordEncoder.encode(novaSenha));
        usuario.setTokenRecuperacao(null);
        usuario.setExpiracaoTokenRecuperacao(null);
        usuario.setTokenPreAutenticacao(null);
        usuario.setExpiracaoPreAutenticacao(null);

        // Encerra as sessões abertas com a senha antiga
        usuario.encerrarSessoes();
        usuarioRepository.save(usuario);
        bloqueioContaService.limparFalhas(usuario);

        auditoriaService.registrar(usuario, "SENHA_REDEFINIDA",
                "Senha redefinida pelo link de recuperação", ipOrigem, true);
        log.info("[RECUPERAÇÃO] Senha redefinida para o usuário id={}", usuario.getId());
    }

    // métodos auxiliares

    // Token válido: existe, não expirou e a conta continua ativa. Qualquer falha devolve vazio, sem dizer o motivo
    private Optional<Usuario> buscarPorTokenValido(String token) {
        if (token == null || token.isBlank() || token.length() > 200) {
            return Optional.empty();
        }
        Optional<Usuario> usuarioDoToken = usuarioRepository.findByTokenRecuperacao(HashToken.sha256(token));
        if (usuarioDoToken.isEmpty()) {
            return Optional.empty();
        }

        Usuario usuario = usuarioDoToken.get();
        boolean tokenAindaValido = usuario.getExpiracaoTokenRecuperacao() != null
                && agora().isBefore(usuario.getExpiracaoTokenRecuperacao());
        boolean contaAtiva = Boolean.TRUE.equals(usuario.getAtivo());

        if (!tokenAindaValido || !contaAtiva) {
            return Optional.empty();
        }
        return Optional.of(usuario);
    }

    // O token atual ainda está no começo da validade, ou seja, foi gerado há menos de 2 minutos
    private boolean pedidoRecente(Usuario usuario) {
        OffsetDateTime expiracao = usuario.getExpiracaoTokenRecuperacao();
        return usuario.getTokenRecuperacao() != null && expiracao != null
                && expiracao.isAfter(agora().plusMinutes(validadeMinutos - INTERVALO_MINIMO_MINUTOS));
    }

    // 32 bytes aleatórios em Base64 próprio para URL, sem os caracteres + e / que quebram o link
    private String gerarToken() {
        byte[] bytes = new byte[32];
        GERADOR.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }


    private OffsetDateTime agora() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
