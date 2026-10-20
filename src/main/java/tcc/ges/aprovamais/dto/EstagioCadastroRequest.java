package tcc.ges.aprovamais.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import tcc.ges.aprovamais.service.ValidadorCnpj;

import java.time.LocalDate;

@Data
public class EstagioCadastroRequest {

    // Limite de quanto a data de início pode estar no futuro, o mesmo usado na aprovação
    public static final int MAXIMO_ANOS_NO_FUTURO = 1;

    @NotNull(message = "Informe a data de início do estágio")
    private LocalDate dataInicio;

    @NotNull(message = "Selecione o perfil do estágio")
    @Positive(message = "Perfil de estágio inválido")
    private Long tipoEstagioId;

    // Aceita com ou sem pontuação
    // 11.111.111/0001-11 ou 11111111111111
    @NotBlank(message = "Informe o CNPJ da empresa")
    @Size(max = 18, message = "CNPJ inválido")
    private String cnpjEmpresa;

    @NotBlank(message = "Informe o nome da empresa")
    @Size(min = 2, max = 150, message = "O nome da empresa deve ter entre 2 e 150 caracteres")
    private String nomeEmpresa;

    /*
       A data pode estar no passado (o aluno às vezes cadastra depois de já ter
       começado) ou um pouco no futuro, mas não mais de 1 ano à frente, que aí
       já é erro de digitação. Valor nulo fica com o @NotNull
    */
    @AssertTrue(message = "A data de início não pode ser mais de 1 ano no futuro")
    public boolean isDataInicioDentroDoLimite() {
        return dataInicioDentroDoLimite(dataInicio);
    }

    // Confere os dígitos verificadores. Valor vazio fica com o @NotBlank
    @AssertTrue(message = "CNPJ inválido. Confira os números digitados")
    public boolean isCnpjComDigitosValidos() {
        return cnpjEmpresa == null || cnpjEmpresa.isBlank() || ValidadorCnpj.valido(cnpjEmpresa);
    }

    public static boolean dataInicioDentroDoLimite(LocalDate data) {
        return data == null || !data.isAfter(LocalDate.now().plusYears(MAXIMO_ANOS_NO_FUTURO));
    }
}
