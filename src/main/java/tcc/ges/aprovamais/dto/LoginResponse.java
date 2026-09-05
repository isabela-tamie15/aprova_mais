package tcc.ges.aprovamais.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import tcc.ges.aprovamais.entity.enums.PerfilUsuario;

@Data
@AllArgsConstructor
public class LoginResponse {

    private String token;
    private String nome;
    private String email;
    private PerfilUsuario perfil;



}
