package tcc.ges.aprovamais.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TipoEstagioRequest {

    @NotNull(message = "Selecione o curso")
    @Positive(message = "Curso inválido")
    private Long cursoId;

    @NotBlank(message = "Informe o nome do tipo de estágio")
    @Size(min = 2, max = 100, message = "O nome deve ter entre 2 e 100 caracteres")
    private String nome;

    @Size(max = 255, message = "A descrição deve ter no máximo 255 caracteres")
    private String descricao;

    // Carga horária total exigida pelo curso nesse tipo (RN31), ex: 270 h
    @NotNull(message = "Informe a carga horária total")
    @DecimalMin(value = "1", message = "A carga horária total deve ser maior que zero")
    @DecimalMax(value = "2000", message = "A carga horária total deve ser de no máximo 2000 horas")
    @Digits(integer = 4, fraction = 2, message = "Use no máximo duas casas decimais na carga horária")
    private BigDecimal cargaHorariaNecessaria;

    /*
       Horas por semana que o tipo abate (RN31). O teto de 44 h vem da Constituição
       (art. 7º, XIII). Para o estágio da Lei 11.788/2008 o teto é 30 h, e cabe ao
       coordenador respeitá-lo ao cadastrar esse tipo
    */
    @NotNull(message = "Informe a jornada semanal")
    @DecimalMin(value = "1", message = "A jornada semanal deve ser maior que zero")
    @DecimalMax(value = "44", message = "A jornada semanal não pode passar de 44 horas")
    @Digits(integer = 2, fraction = 2, message = "Use no máximo duas casas decimais na jornada")
    private BigDecimal jornadaSemanal;
}
