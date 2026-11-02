package tcc.ges.aprovamais.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import tcc.ges.aprovamais.entity.enums.Modalidade;
import tcc.ges.aprovamais.entity.enums.Periodo;

import java.time.LocalDate;

@Data
public class TurmaRequest {

    @NotNull(message = "Selecione o curso")
    @Positive(message = "Curso inválido")
    private Long cursoId;

    // Ano e semestre, no mesmo formato que o seed e a RN22 usam (ex: 2026/2)
    @NotBlank(message = "Informe o semestre")
    @Pattern(regexp = "^\\d{4}/[12]$", message = "Informe o semestre no formato AAAA/1 ou AAAA/2")
    private String semestre;

    @NotBlank(message = "Informe o identificador da turma")
    @Size(max = 20, message = "O identificador deve ter no máximo 20 caracteres")
    private String identificador;

    @NotNull(message = "Selecione o período")
    private Periodo periodo;

    @NotNull(message = "Selecione a modalidade")
    private Modalidade modalidade;

    /*
       Data em que as entregas do semestre fecham, usada no aviso de prazo da F2.
       É opcional na API, igual à coluna, que aceita vazio: turma sem a data mostra
       "ainda não informada pela coordenação" ao aluno e ao orientador. A tela de
       turmas continua exigindo o campo, na validação do coordenador-turmas.js
    */
    private LocalDate dataEncerramentoEntregas;
}
