package tcc.ges.aprovamais.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/*
   Visão consolidada de um aluno para o orientador, contém o progresso do estágio,
   com as mesmas contas do painel e as tarefas da trilha na ordem
*/
@Getter
@Builder
public class VisaoAlunoResponse {
    private final EstagioResponse progresso;
    private final List<TarefaVisaoAlunoResponse> tarefas;
}
