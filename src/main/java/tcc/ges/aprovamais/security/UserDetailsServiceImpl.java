package tcc.ges.aprovamais.security;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import tcc.ges.aprovamais.entity.Usuario;
import tcc.ges.aprovamais.repository.UsuarioRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private static final Logger log =
            LoggerFactory.getLogger(UserDetailsServiceImpl.class);

    private final UsuarioRepository usuarioRepository;

    // Busca o usuário e monta os dados de autenticação do Spring Security
    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        log.debug("[AUTH] Tentativa de autenticação para: {}", email);

        // Busca pelo e-mail e lança exceção se não achar
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("[AUTH] Usuário não encontrado: {}", email);
                    return new UsernameNotFoundException(
                            "Usuário não encontrado com e-mail: " + email
                    );
                });

        log.info("[AUTH] Usuário carregado com sucesso: {} - Perfil: {}",
                usuario.getEmail(), usuario.getPerfil());

        return paraUserDetails(usuario);
    }

    // Monta o UserDetails a partir do usuário, usado no login e no JwtFilter
    public static UserDetails paraUserDetails(Usuario usuario) {
        /*
           O prefixo ROLE_ é a convenção do Spring para o hasRole. O disabled e o accountLocked vêm do banco,
           e os outros dois ficam false porque não há expiração de conta nem de senha
        */
        return User.builder()
                .username(usuario.getEmail())
                .password(usuario.getSenhaHash())
                .authorities(List.of(new SimpleGrantedAuthority(
                        "ROLE_" + usuario.getPerfil().name()
                )))
                .disabled(!usuario.getAtivo())
                .accountLocked(usuario.getContaBloqueada())
                .accountExpired(false)
                .credentialsExpired(false)
                .build();
    }
}