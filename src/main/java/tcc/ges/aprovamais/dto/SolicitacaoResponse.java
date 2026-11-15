package tcc.ges.aprovamais.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Builder
public class SolicitacaoResponse {
    private final Long id;
    private final String tipo;
    private final String status;
    private final String motivo;
    private final String justificativaReprovacao;
    private final Long estagioId;
    private final String nomeAluno;
    private final String turma;
    private final String nomeCurso;
    private final String nomeTipoEstagioAtual;
    private final String nomeNovoTipoEstagio;
    private final String nomeAnalistaSecretaria;
    private final OffsetDateTime analisadaSecretariaEm;
    private final String nomeAnalistaCoordenacao;
    private final OffsetDateTime analisadaCoordenacaoEm;
    private final LocalDate prazoArrependimento;
    private final OffsetDateTime criadoEm;
}
