package tcc.ges.aprovamais.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/*
   Regra das atividades da trilha, separada do service para ser testada sem banco. Recebe o total
   de tarefas e as entregas aprovadas e diz quantas atividades faltam e se ainda há pendência
*/
public final class PendenciasEstagio {

    private PendenciasEstagio() {
    }

    // Atividades que faltam aprovar, o total da trilha menos as aprovadas, nunca abaixo de zero
    public static long calcularAtividadesRestantes(long total, long aprovadas) {
        return Math.max(total - aprovadas, 0);
    }

    /*
       Há pendência quando a trilha tem pelo menos uma tarefa e nem todas foram aprovadas. Trilha sem
       tarefas não tem pendência, e por isso também não gera aviso de prazo
    */
    public static boolean temPendencia(long total, long aprovadas) {
        return total > 0 && aprovadas < total;
    }

    /*
       As horas não fecham no prazo quando a data prevista de conclusão cai depois do encerramento
       das entregas da turma. Datas iguais contam como no prazo, e sem uma das duas datas não há
       como comparar, então não conta como pendência
    */
    public static boolean temPendenciaDeHoras(LocalDate dataPrevistaConclusao, LocalDate dataEncerramentoEntregas) {
        if (dataPrevistaConclusao == null || dataEncerramentoEntregas == null) {
            return false;
        }
        return dataPrevistaConclusao.isAfter(dataEncerramentoEntregas);
    }

    /*
       O estágio pode ser concluído quando a carga cumprida chega à exigida e todas as tarefas da
       trilha estão aprovadas. Trilha sem tarefas depende só da carga
    */
    public static boolean podeConcluir(BigDecimal cargaCumprida, BigDecimal cargaNecessaria, long total, long aprovadas) {
        if (cargaCumprida == null || cargaNecessaria == null) {
            return false;
        }
        return cargaCumprida.compareTo(cargaNecessaria) >= 0 && !temPendencia(total, aprovadas);
    }
}
