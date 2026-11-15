package tcc.ges.aprovamais.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/*
   Resposta do dashboard do coordenador. Os números já vêm prontos do PainelCoordenadorService,
   inclusive a largura das barras e o texto do prazo, e a tela só mostra.
   As linhas dos três gráficos ficam como classes neste mesmo arquivo
*/
@Getter
@Builder
public class PainelCoordenadorResponse {

    // Cartão 1, estágios PENDENTE ainda sem orientador
    private final long estagiosSemOrientador;
    // Cartão 2, estágios PENDENTE com orientador, esperando a validação
    private final long estagiosAguardandoValidacao;
    // Cartão 3, estágios ATIVO
    private final long estagiosEmAndamento;
    // Cartão 4, turmas com estágio ATIVO e sem a data de encerramento das entregas
    private final long turmasSemDataEncerramento;
    // Cartão 5, entregas ENVIADA ou EM_REVISAO esperando a correção do orientador
    private final long tarefasAguardandoCorrecao;
    // Cartão 6, estágios na reta final com pendências, e as três linhas que somam esse número
    private final long estagiosRetaFinal;
    private final long retaFinalSoTarefas;
    private final long retaFinalSoHoras;
    private final long retaFinalDuasPendencias;

    // Gráfico 1, os cartões 1, 2 e 3 separados por turma
    private final List<SituacaoTurma> situacaoPorTurma;
    // Gráfico 2, o cartão 5 separado por turma e orientador, do maior para o menor
    private final List<CorrecaoTurma> correcaoPorTurma;
    // Gráfico 3, o cartão 6 separado por turma, com a faixa do prazo
    private final List<PrazoTurma> prazoPorTurma;

    /*
       Uma linha do gráfico 1. O percentualBarra é o tamanho da barra comparado com a maior turma,
       que fica com 100. Os outros três percentuais dividem a barra entre as partes
    */
    @Getter
    @Builder
    public static class SituacaoTurma {
        private final Long turmaId;
        private final String turma;
        private final String semestre;
        private final String curso;
        private final long semOrientador;
        private final long aguardandoValidacao;
        private final long emAndamento;
        private final long total;
        private final double percentualBarra;
        private final double percentualSemOrientador;
        private final double percentualAguardandoValidacao;
        private final double percentualEmAndamento;
    }

    // Uma linha do gráfico 2, com as tarefas esperando correção de uma turma e o orientador
    @Getter
    @Builder
    public static class CorrecaoTurma {
        private final Long turmaId;
        private final String turma;
        private final String semestre;
        private final String curso;
        private final String orientador;
        private final long quantidade;
        private final double percentualBarra;
    }

    /*
       Uma linha do gráfico 3. A faixa é o aviso de prazo (AMARELO, VERMELHO, ULTIMO_DIA ou ENCERRADO)
       e o textoPrazo é a frase que aparece embaixo da turma
    */
    @Getter
    @Builder
    public static class PrazoTurma {
        private final Long turmaId;
        private final String turma;
        private final String semestre;
        private final String curso;
        private final long quantidade;
        private final long diasRestantes;
        private final String faixa;
        private final String textoPrazo;
        private final double percentualBarra;
    }
}
