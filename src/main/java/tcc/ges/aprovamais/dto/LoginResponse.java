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
    private final boolean requerConfiguracao2FA;

    public static LoginResponse requer2FA(String preAuthToken) {
        return LoginResponse.builder()
                .token(preAuthToken)
                .requer2FA(true)
                .build();
    }


    public LoginResponse semToken() {
        return LoginResponse.builder()
                .nome(nome)
                .email(email)
                .perfil(perfil)
                .build();
    }

    // perfil que exige 2FA mas ainda não configurou, o login só é concluído após a configuração
    public static LoginResponse requerConfiguracao2FA(String preAuthToken) {
        return LoginResponse.builder()
                .token(preAuthToken)
                .requerConfiguracao2FA(true)
                .build();
    }
}