package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.entity.Administrador;
import tcc.ges.aprovamais.entity.Secretaria;
import tcc.ges.aprovamais.entity.Usuario;
import tcc.ges.aprovamais.entity.enums.PerfilUsuario;
import tcc.ges.aprovamais.repository.UsuarioRepository;


/*
   Cria as contas iniciais na primeira vez que o sistema sobe. A conta do administrador é a
   definitiva. A da secretaria inicial garante o acesso enquanto o coordenador não convida
   secretarias pela tela
*/
@Service
@RequiredArgsConstructor
public class ContaInicialService {

    private static final Logger log = LoggerFactory.getLogger(ContaInicialService.class);
    private static final int TAMANHO_MINIMO_SENHA = 8;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;

    /*
       Cria o administrador inicial se ainda não existir nenhum. Ele nasce sem 2FA, então o primeiro
       login já cai na configuração obrigatória
    */
    @Transactional
    public void criarAdministradorInicialSeNecessario(String email, String senha, String nome) {
        if (!podeCriar(PerfilUsuario.ADMINISTRADOR, email, senha, "ADMIN_INICIAL")) {
            return;
        }

        Administrador administrador = new Administrador();
        preencherConta(administrador, PerfilUsuario.ADMINISTRADOR, email, senha, nome);
        usuarioRepository.save(administrador);

        auditoriaService.registrar(administrador, "CONTA_INICIAL_CRIADA",
                "Administrador inicial criado na inicialização do sistema", null, true);

        log.info("[CONTA INICIAL] Administrador inicial criado: {}", email);
    }

    // Cria a secretaria inicial se ainda não existir nenhuma (provisório, veja o comentário da classe lá em cima)
    @Transactional
    public void criarSecretariaInicialSeNecessario(String email, String senha, String nome) {
        if (!podeCriar(PerfilUsuario.SECRETARIA, email, senha, "SECRETARIA_INICIAL")) {
            return;
        }

        Secretaria secretaria = new Secretaria();
        preencherConta(secretaria, PerfilUsuario.SECRETARIA, email, senha, nome);
        secretaria.setSetor("Secretaria Acadêmica");
        usuarioRepository.save(secretaria);

        auditoriaService.registrar(secretaria, "CONTA_INICIAL_CRIADA",
                "Secretaria inicial criada na inicialização do sistema", null, true);

        log.info("[CONTA INICIAL] Secretaria inicial criada: {}", email);
    }

    /*
       Conferências antes de criar a conta, cada uma com a sua mensagem no log. Se algo faltar, o sistema
       sobe do mesmo jeito e só avisa. O prefixo é o começo do nome das variáveis de ambiente
    */
    private boolean podeCriar(PerfilUsuario perfil, String email, String senha, String prefixo) {

        if (usuarioRepository.existsByPerfil(perfil)) {
            log.debug("[CONTA INICIAL] Já existe {} cadastrado; nenhuma ação necessária.", perfil);
            return false;
        }

        // Se não tem a conta e as credenciais não foram configuradas, só manda um aviso
        if (email == null || email.isBlank() || senha == null || senha.isBlank()) {
            log.warn("[CONTA INICIAL] Nenhum {} cadastrado e {}_EMAIL/{}_SENHA não configurados.",
                    perfil, prefixo, prefixo);
            return false;
        }

        // Senha curta demais, recusa por segurança
        if (senha.length() < TAMANHO_MINIMO_SENHA) {
            log.error("[CONTA INICIAL] {}_SENHA deve ter no mínimo {} caracteres. Conta {} não criada.",
                    prefixo, TAMANHO_MINIMO_SENHA, perfil);
            return false;
        }

        // O e-mail já pertence a outro usuário
        if (usuarioRepository.existsByEmail(email)) {
            log.error("[CONTA INICIAL] O e-mail {} já pertence a outro usuário. Conta {} não criada.",
                    email, perfil);
            return false;
        }
        return true;
    }

    /*
       Preenche os campos comuns. A conta já sai ativa e sem convite, e o 2FA fica desligado para o
       primeiro login obrigar a configuração
    */
    private void preencherConta(Usuario usuario, PerfilUsuario perfil, String email, String senha, String nome) {
        usuario.setNome(nome);
        usuario.setEmail(email);
        usuario.setSenhaHash(passwordEncoder.encode(senha));
        usuario.setPerfil(perfil);
        usuario.setAtivo(true);
        usuario.setPrimeiroAcesso(false);
        usuario.setDoisFatoresAtivo(false);
        usuario.setTentativasFalhas(0);
        usuario.setContaBloqueada(false);
    }
}
