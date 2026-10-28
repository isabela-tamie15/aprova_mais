package tcc.ges.aprovamais.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/*
   Filtros da consulta da auditoria, eles vêm na query string, e todos são
   opcionais, se n tiver nenhum, a consulta lista tudo, do mais recente para o mais antigo
*/
@Data
public class FiltroAuditoriaRequest {

    // Email completo de quem gerou o registro, ou só uma parte dele, sem diferenciar maiúscula
    @Size(max = 254, message = "O e-mail deve ter no máximo 254 caracteres")
    private String email;

    // Nome exato da ação, como LOGIN_FALHA ou ESTAGIO_REJEITADO
    @Size(max = 100, message = "A ação deve ter no máximo 100 caracteres")
    private String acao;

    // true só os sucessos, false só as falhas, vazio os dois
    private Boolean sucesso;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate inicio;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fim;

    @Min(value = 0, message = "A página não pode ser negativa")
    private Integer pagina = 0;

    @Min(value = 1, message = "O tamanho da página deve ser de 1 a 100")
    @Max(value = 100, message = "O tamanho da página deve ser de 1 a 100")
    private Integer tamanho = 20;

    @AssertTrue(message = "A data inicial deve ser anterior ou igual à data final")
    public boolean isPeriodoValido() {
        return inicio == null || fim == null || !inicio.isAfter(fim);
    }
}
