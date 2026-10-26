package tcc.ges.aprovamais.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CursoRequest {

    @NotBlank(message = "Informe o nome do curso")
    @Size(min = 3, max = 150, message = "O nome do curso deve ter entre 3 e 150 caracteres")
    private String nome;

    // Código da oferta no e-MEC (curso, instituição e unidade), único no sistema (RN19)
    @NotBlank(message = "Informe o código e-MEC do curso")
    @Size(max = 20, message = "O código e-MEC deve ter no máximo 20 caracteres")
    @Pattern(regexp = "^[0-9A-Za-z-]+$", message = "O código e-MEC deve ter apenas letras, números e hífen")
    private String codigoMec;
}
