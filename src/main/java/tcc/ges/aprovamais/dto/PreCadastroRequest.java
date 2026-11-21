package tcc.ges.aprovamais.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import tcc.ges.aprovamais.entity.enums.PerfilDestino;

// Dados que o coordenador informa para autorizar alguém a receber convite
@Data
public class PreCadastroRequest {

    @NotBlank(message = "Informe o nome completo")
    @Size(min = 3, max = 150, message = "O nome deve ter entre 3 e 150 caracteres")
    private String nome;

    @NotBlank(message = "Informe o e-mail")
    @Email(message = "Informe um e-mail válido")
    @Size(max = 254, message = "O e-mail deve ter no máximo 254 caracteres")
    private String email;

    @NotNull(message = "Escolha o perfil")
    private PerfilDestino perfil;

    // RGM do aluno ou matrícula institucional do orientador
    @NotBlank(message = "Informe o RGM ou a matrícula institucional")
    @Size(max = 50, message = "O identificador deve ter no máximo 50 caracteres")
    @Pattern(regexp = "^[0-9A-Za-z.-]+$", message = "Use apenas letras, números, ponto e hífen no identificador")
    private String identificadorInstitucional;

    // Obrigatória para aluno e ignorada para orientador
    private Long turmaId;

    // O pré-cadastro é só para aluno e orientador. Os outros perfis são convidados direto
    @AssertTrue(message = "O pré-cadastro é só para aluno ou orientador")
    public boolean isPerfilDePreCadastro() {
        return perfil == null || perfil == PerfilDestino.ALUNO || perfil == PerfilDestino.ORIENTADOR;
    }

    // O RGM do aluno tem só números. A matrícula do orientador segue o formato geral do identificador
    @AssertTrue(message = "O RGM do aluno deve conter apenas números")
    public boolean isRgmNumericoParaAluno() {
        return perfil != PerfilDestino.ALUNO || identificadorInstitucional == null
                || identificadorInstitucional.isBlank() || identificadorInstitucional.trim().matches("\\d+");
    }

    // Aluno sem turma é recusado
    @AssertTrue(message = "Escolha a turma do aluno")
    public boolean isTurmaInformadaParaAluno() {
        return perfil != PerfilDestino.ALUNO || turmaId != null;
    }
}
