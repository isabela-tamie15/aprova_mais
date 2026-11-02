package tcc.ges.aprovamais.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import tcc.ges.aprovamais.entity.enums.PerfilDestino;

@Data
public class ConviteRequest {

    @NotBlank(message = "O e-mail é obrigatório")
    @Email(message = "E-mail inválido")
    @Size(max = 254, message = "O e-mail deve ter no máximo 254 caracteres")
    private String emailDestino;

    @NotNull(message = "O perfil é obrigatório")
    private PerfilDestino perfil;

    // Nome do convidado, só para coordenador e secretaria, o aluno e orientador vem do pré-cadastro
    @Size(max = 150, message = "O nome deve ter no máximo 150 caracteres")
    private String nome;

    // Coordenador e secretaria não passam pelo pré-cadastro, então o nome vem aqui
    @AssertTrue(message = "Informe o nome completo")
    public boolean isNomeInformadoParaPerfilAdministrativo() {
        boolean administrativo = perfil == PerfilDestino.COORDENADOR || perfil == PerfilDestino.SECRETARIA;
        return !administrativo || (nome != null && nome.trim().length() >= 3);
    }
}