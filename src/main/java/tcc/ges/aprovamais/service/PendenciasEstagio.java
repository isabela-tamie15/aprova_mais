package tcc.ges.aprovamais.service;

import java.time.LocalDate;

/*
   F2: regra das atividades da trilha, separada do service para dar para testar sem banco,
   do mesmo jeito que a CalculadoraCargaHoraria. Ela recebe os números já contados pelo
   EstagioService (total de tarefas da trilha e entregas aprovadas) e responde quantas
   atividades faltam e se ainda existe pendência. O dashboard pode reaproveitar esta classe depois
*/
public final class PendenciasEstagio {

    private PendenciasEstagio() {
    }

    // Atividades que ainda faltam ser aprovadas: total da trilha menos as aprovadas, nunca menor que zero
    public static long calcularAtividadesRestantes(long total, long aprovadas) {
        return Math.max(total - aprovadas, 0);
    }

    /*
       Tem pendência quando a trilha tem pelo menos uma tarefa e nem todas foram aprovadas.
       Trilha sem tarefas não tem pendência, porque não existe atividade para entregar,
       e por isso o aluno e o orientador também não recebem o aviso de prazo
    */
    public static boolean temPendencia(long total, long aprovadas) {
        return total > 0 && aprovadas < total;
    }

    /*
       Dashboard do coordenador: as horas não fecham no prazo quando a data prevista de conclusão
       (o dia em que o aluno completa a carga horária) cai depois da data de encerramento das
       entregas da turma. Se as duas datas forem iguais, o aluno completa as horas no próprio
       último dia, e por isso consideramos que fecham no prazo. Sem uma das duas datas não temos
       como comparar, então não contamos como pendência
    */
    public static boolean temPendenciaDeHoras(LocalDate dataPrevistaConclusao, LocalDate dataEncerramentoEntregas) {
        if (dataPrevistaConclusao == null || dataEncerramentoEntregas == null) {
            return false;
        }
        return dataPrevistaConclusao.isAfter(dataEncerramentoEntregas);
    }
}
