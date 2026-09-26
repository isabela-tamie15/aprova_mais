package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.entity.Secretaria;
import tcc.ges.aprovamais.entity.enums.PerfilUsuario;
import tcc.ges.aprovamais.repository.SecretariaRepository;
import tcc.ges.aprovamais.repository.UsuarioRepository;


// Essa service é quem cria a secretaria inicial na primeira vez que o sistema sobe
@Service
@RequiredArgsConstructor
public class ContaInicialService {

    private static final Logger log = LoggerFactory.getLogger(ContaInicialService.class);
    private static final int TAMANHO_MINIMO_SENHA = 8;

    private final UsuarioRepository usuarioRepository;
    private final SecretariaRepository secretariaRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;

    /*
       Tenta criar a secretaria inicial se ainda não existir. Tem vários
       check antes de criar de fato, cada um com uma mensagem específica
       no log. A ideia é que, se algo estiver faltando, o sistema sobe do
       mesmo jeito, só avisa no log em vez de quebrar a inicialização
    */
    @Transactional
    public void criarSecretariaInicialSeNecessario(String email, String senha, String nome) {

        // Já tem secretaria? Então não faz nada, é o caso normal depois do primeiro boot
        if (usuarioRepository.existsByPerfil(PerfilUsuario.SECRETARIA)) {
            log.debug("[CONTA INICIAL] Já existe secretaria cadastrada; nenhuma ação necessária.");
            return;
        }

        /*
           Não tem secretaria e as credenciais não foram configuradas. Só
           avisa, porque sem secretaria ninguém consegue enviar convite, mas
           o sistema ainda pode funcionar pro resto
        */
        if (email == null || email.isBlank() || senha == null || senha.isBlank()) {
            log.warn("[CONTA INICIAL] Nenhuma secretaria cadastrada e SECRETARIA_INICIAL_EMAIL/"
                    + "SECRETARIA_INICIAL_SENHA não configurados. Não será possível enviar convites.");
            return;
        }

        // Senha curta demais, recusa por segurança
        if (senha.length() < TAMANHO_MINIMO_SENHA) {
            log.error("[CONTA INICIAL] SECRETARIA_INICIAL_SENHA deve ter no mínimo {} caracteres. "
                    + "Secretaria inicial não criada.", TAMANHO_MINIMO_SENHA);
            return;
        }

        // E-mail já pertence a outro usuário, então não pode usar
        if (usuarioRepository.existsByEmail(email)) {
            log.error("[CONTA INICIAL] O e-mail {} já pertence a outro usuário. "
                    + "Secretaria inicial não criada.", email);
            return;
        }

        /*
           Passou por todos os check, então cria a secretaria de fato.
           Os campos ativo, primeiroAcesso e doisFatoresAtivo já vêm
           preenchidos pra conta funcionar direto, sem precisar passar
           pelo fluxo de primeiro acesso
        */
        Secretaria secretaria = new Secretaria();
        secretaria.setNome(nome);
        secretaria.setEmail(email);
        secretaria.setSenhaHash(passwordEncoder.encode(senha));
        secretaria.setPerfil(PerfilUsuario.SECRETARIA);
        secretaria.setSetor("Secretaria Acadêmica");
        secretaria.setAtivo(true);
        secretaria.setPrimeiroAcesso(false);
        secretaria.setDoisFatoresAtivo(false);
        secretaria.setTentativasFalhas(0);
        secretaria.setContaBloqueada(false);

        secretariaRepository.save(secretaria);

        auditoriaService.registrar(secretaria, "CONTA_INICIAL_CRIADA",
                "Secretaria inicial criada na inicialização do sistema", null, true);

        log.info("[CONTA INICIAL] Secretaria inicial criada: {}", email);
    }
}
