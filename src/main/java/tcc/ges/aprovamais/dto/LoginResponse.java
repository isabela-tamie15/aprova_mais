package tcc.ges.aprovamais.dto;

import lombok.Builder;
import lombok.Getter;
import tcc.ges.aprovamais.entity.enums.PerfilUsuario;

@Getter
@Builder
public class LoginResponse {

    private final String token;
    private final String nome;
    private final String email;
    private final PerfilUsuario perfil;
    private final boolean requer2FA;

    public static LoginResponse requer2FA(String preAuthToken) {
        return LoginResponse.builder()
                .token(preAuthToken)
                .requer2FA(true)
                .build();
    }
}