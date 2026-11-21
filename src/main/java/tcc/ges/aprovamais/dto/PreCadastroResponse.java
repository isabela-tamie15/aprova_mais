package tcc.ges.aprovamais.dto;

import lombok.Builder;
import lombok.Getter;

// Uma linha da lista de pré-cadastro do coordenador
@Getter
@Builder
public class PreCadastroResponse {
    private final Long id;
    private final String nome;
    private final String email;
    private final String perfil;
    private final String identificadorInstitucional;
    private final Long turmaId;
    private final String descricaoTurma;
    private final String nomeCurso;

    // AGUARDANDO_CONVITE, CONVITE_PENDENTE ou CADASTRO_CONCLUIDO, para o coordenador acompanhar
    private final String situacao;
}
