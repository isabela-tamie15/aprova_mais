package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.dto.PainelCoordenadorResponse;
import tcc.ges.aprovamais.entity.Entrega;
import tcc.ges.aprovamais.entity.Estagio;
import tcc.ges.aprovamais.entity.Turma;
import tcc.ges.aprovamais.entity.enums.StatusEntrega;
import tcc.ges.aprovamais.entity.enums.StatusEstagio;
import tcc.ges.aprovamais.repository.PainelCoordenadorRepository;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/*
   Dashboard do coordenador. Este service só lê dados que já existem (estágios, turmas e entregas)
   e monta os 6 cartões da jornada e os 3 gráficos por turma. Nenhum dado é alterado.
   As regras de prazo e de pendência são as mesmas da F2 (CalculadoraCargaHoraria e PendenciasEstagio),
   então o aluno, o orientador e o coordenador sempre veem os mesmos números
*/
@Service
@RequiredArgsConstructor
public class PainelCoordenadorService {

    private static final Logger log = LoggerFactory.getLogger(PainelCoordenadorService.class);

    // Status que entram no painel: PENDENTE (cartões 1 e 2) e ATIVO (cartões 3, 4 e 6)
    private static final Set<StatusEstagio> STATUS_DO_PAINEL = EnumSet.of(StatusEstagio.PENDENTE, StatusEstagio.ATIVO);

    // Quando duas turmas empatam no número, a ordem é: semestre mais recente, depois identificador e curso
    private static final Comparator<Turma> ORDEM_DAS_TURMAS = Comparator
            .comparing(Turma::getSemestre, Comparator.reverseOrder())
            .thenComparing(Turma::getIdentificador)
            .thenComparing(turma -> turma.getCurso().getNome());

    private final PainelCoordenadorRepository painelCoordenadorRepository;
    private final CursoService cursoService;

    /*
       Monta o painel do coordenador logado. O cursoId é opcional: sem ele, entram todos os cursos
       do coordenador ("Todos os meus cursos"); com ele, só aquele curso. Antes de tudo conferimos
       se o curso é mesmo do coordenador, e se não for o CursoService responde 404 (RN17 e RN21)
    */
    @Transactional(readOnly = true)
    public PainelCoordenadorResponse montarPainel(String emailCoordenador, Long cursoId) {
        if (cursoId != null) {
            cursoService.buscarCursoDoCoordenador(cursoId, emailCoordenador);
        }
        LocalDate hoje = LocalDate.now();

        // Passo 1: buscamos no banco tudo o que o painel precisa, em 4 consultas
        List<Estagio> estagios = painelCoordenadorRepository
                .listarEstagiosDosCursos(emailCoordenador, STATUS_DO_PAINEL)
                .stream()
                .filter(estagio -> doCursoEscolhido(estagio, cursoId))
                .toList();
        Map<Long, Long> tarefasPorTipoEstagio = contarRepeticoes(
                painelCoordenadorRepository.listarTipoEstagioDasTarefas(emailCoordenador));
        Map<Long, Long> aprovadasPorEstagio = contarRepeticoes(
                painelCoordenadorRepository.listarEstagioDasEntregas(
                        emailCoordenador, StatusEstagio.ATIVO, StatusEntrega.APROVADA));
        List<Entrega> entregasAguardando = painelCoordenadorRepository
                .listarEntregasDosCursos(emailCoordenador, CorrecaoService.AGUARDANDO_ANALISE)
                .stream()
                .filter(entrega -> doCursoEscolhido(entrega.getEstagio(), cursoId))
                .toList();

        // Passo 2: passamos por cada estágio e somamos nos cartões e nas linhas de cada turma
        long semOrientador = 0;
        long aguardandoValidacao = 0;
        long emAndamento = 0;
        long soTarefas = 0;
        long soHoras = 0;
        long duasPendencias = 0;
        Set<Long> turmasSemData = new HashSet<>();
        Map<Long, LinhaSituacao> situacaoPorTurma = new HashMap<>();
        Map<Long, LinhaPrazo> prazoPorTurma = new HashMap<>();

        for (Estagio estagio : estagios) {
            Turma turma = estagio.getMatricula().getTurma();
            LinhaSituacao situacao = situacaoPorTurma.computeIfAbsent(turma.getId(), id -> new LinhaSituacao(turma));

            // Cartões 1 e 2: o estágio PENDENTE fica sem orientador quando a turma não tem orientador vinculado
            if (estagio.getStatus() == StatusEstagio.PENDENTE) {
                if (estagio.getOrientador() == null) {
                    semOrientador++;
                    situacao.semOrientador++;
                } else {
                    aguardandoValidacao++;
                    situacao.aguardandoValidacao++;
                }
                continue;
            }

            // Daqui para baixo o estágio é ATIVO (cartão 3)
            emAndamento++;
            situacao.emAndamento++;

            // Cartão 4: sem a data de encerramento, o aviso de prazo fica desligado para os alunos da turma
            LocalDate dataEncerramento = turma.getDataEncerramentoEntregas();
            Long diasRestantes = CalculadoraCargaHoraria.calcularDiasRestantes(dataEncerramento, hoje);
            if (diasRestantes == null) {
                turmasSemData.add(turma.getId());
                continue;
            }

            // Reta final: a mesma faixa do aviso de prazo da F2 (de 30 dias antes até depois da data)
            String faixa = CalculadoraCargaHoraria.definirAvisoPrazo(diasRestantes);
            if (faixa == null) {
                continue;
            }

            // Cartão 6: as duas pendências, cada uma com a sua regra da PendenciasEstagio
            long totalTarefas = tarefasPorTipoEstagio.getOrDefault(estagio.getTipoEstagio().getId(), 0L);
            long tarefasAprovadas = aprovadasPorEstagio.getOrDefault(estagio.getId(), 0L);
            boolean pendenciaTarefas = PendenciasEstagio.temPendencia(totalTarefas, tarefasAprovadas);
            boolean pendenciaHoras = PendenciasEstagio.temPendenciaDeHoras(dataPrevista(estagio), dataEncerramento);

            // Cada estágio entra em uma linha só, para as três linhas somarem o número do cartão
            if (pendenciaTarefas && pendenciaHoras) {
                duasPendencias++;
            } else if (pendenciaTarefas) {
                soTarefas++;
            } else if (pendenciaHoras) {
                soHoras++;
            } else {
                continue;
            }
            prazoPorTurma.computeIfAbsent(turma.getId(), id -> new LinhaPrazo(turma, diasRestantes, faixa))
                    .quantidade++;
        }

        // Cartão 5 e gráfico 2: entregas aguardando correção, por turma e orientador do estágio
        Map<String, LinhaCorrecao> correcaoPorTurma = new HashMap<>();
        for (Entrega entrega : entregasAguardando) {
            Estagio estagio = entrega.getEstagio();
            Turma turma = estagio.getMatricula().getTurma();
            Long orientadorId = estagio.getOrientador() != null ? estagio.getOrientador().getId() : null;
            String orientador = estagio.getOrientador() != null ? estagio.getOrientador().getNome() : "Sem orientador";
            correcaoPorTurma.computeIfAbsent(turma.getId() + "-" + orientadorId,
                    chave -> new LinhaCorrecao(turma, orientador)).quantidade++;
        }

        log.debug("Painel do coordenador montado: {} estágios e {} entregas aguardando correção",
                estagios.size(), entregasAguardando.size());

        // Passo 3: montamos a resposta, com as listas já ordenadas e as barras já calculadas
        return PainelCoordenadorResponse.builder()
                .estagiosSemOrientador(semOrientador)
                .estagiosAguardandoValidacao(aguardandoValidacao)
                .estagiosEmAndamento(emAndamento)
                .turmasSemDataEncerramento(turmasSemData.size())
                .tarefasAguardandoCorrecao(entregasAguardando.size())
                .estagiosRetaFinal(soTarefas + soHoras + duasPendencias)
                .retaFinalSoTarefas(soTarefas)
                .retaFinalSoHoras(soHoras)
                .retaFinalDuasPendencias(duasPendencias)
                .situacaoPorTurma(montarSituacao(situacaoPorTurma.values()))
                .correcaoPorTurma(montarCorrecao(correcaoPorTurma.values()))
                .prazoPorTurma(montarPrazo(prazoPorTurma.values()))
                .build();
    }

    // Gráfico 1: do maior total para o menor. A maior turma fica com a barra cheia (100)
    private List<PainelCoordenadorResponse.SituacaoTurma> montarSituacao(Collection<LinhaSituacao> linhas) {
        List<LinhaSituacao> ordenadas = linhas.stream()
                .sorted(Comparator.comparingLong(LinhaSituacao::total).reversed()
                        .thenComparing(linha -> linha.turma, ORDEM_DAS_TURMAS))
                .toList();
        long maior = ordenadas.isEmpty() ? 0 : ordenadas.get(0).total();

        return ordenadas.stream()
                .map(linha -> PainelCoordenadorResponse.SituacaoTurma.builder()
                        .turmaId(linha.turma.getId())
                        .turma(linha.turma.getIdentificador())
                        .semestre(linha.turma.getSemestre())
                        .curso(linha.turma.getCurso().getNome())
                        .semOrientador(linha.semOrientador)
                        .aguardandoValidacao(linha.aguardandoValidacao)
                        .emAndamento(linha.emAndamento)
                        .total(linha.total())
                        .percentualBarra(percentual(linha.total(), maior))
                        .percentualSemOrientador(percentual(linha.semOrientador, linha.total()))
                        .percentualAguardandoValidacao(percentual(linha.aguardandoValidacao, linha.total()))
                        .percentualEmAndamento(percentual(linha.emAndamento, linha.total()))
                        .build())
                .toList();
    }

    // Gráfico 2: do maior número de tarefas para o menor
    private List<PainelCoordenadorResponse.CorrecaoTurma> montarCorrecao(Collection<LinhaCorrecao> linhas) {
        List<LinhaCorrecao> ordenadas = linhas.stream()
                .sorted(Comparator.comparingLong((LinhaCorrecao linha) -> linha.quantidade).reversed()
                        .thenComparing(linha -> linha.turma, ORDEM_DAS_TURMAS)
                        .thenComparing(linha -> linha.orientador))
                .toList();
        long maior = ordenadas.isEmpty() ? 0 : ordenadas.get(0).quantidade;

        return ordenadas.stream()
                .map(linha -> PainelCoordenadorResponse.CorrecaoTurma.builder()
                        .turmaId(linha.turma.getId())
                        .turma(linha.turma.getIdentificador())
                        .semestre(linha.turma.getSemestre())
                        .curso(linha.turma.getCurso().getNome())
                        .orientador(linha.orientador)
                        .quantidade(linha.quantidade)
                        .percentualBarra(percentual(linha.quantidade, maior))
                        .build())
                .toList();
    }

    // Gráfico 3: do maior número de estágios para o menor; no empate, a turma com mais dias vem antes
    private List<PainelCoordenadorResponse.PrazoTurma> montarPrazo(Collection<LinhaPrazo> linhas) {
        List<LinhaPrazo> ordenadas = linhas.stream()
                .sorted(Comparator.comparingLong((LinhaPrazo linha) -> linha.quantidade).reversed()
                        .thenComparing(Comparator.comparingLong((LinhaPrazo linha) -> linha.diasRestantes).reversed())
                        .thenComparing(linha -> linha.turma, ORDEM_DAS_TURMAS))
                .toList();
        long maior = ordenadas.isEmpty() ? 0 : ordenadas.get(0).quantidade;

        return ordenadas.stream()
                .map(linha -> PainelCoordenadorResponse.PrazoTurma.builder()
                        .turmaId(linha.turma.getId())
                        .turma(linha.turma.getIdentificador())
                        .semestre(linha.turma.getSemestre())
                        .curso(linha.turma.getCurso().getNome())
                        .quantidade(linha.quantidade)
                        .diasRestantes(linha.diasRestantes)
                        .faixa(linha.faixa)
                        .textoPrazo(textoPrazo(linha.diasRestantes))
                        .percentualBarra(percentual(linha.quantidade, maior))
                        .build())
                .toList();
    }

    /*
       Frase do prazo que aparece embaixo da turma no gráfico 3. Exemplos: 15 → "faltam 15 dias",
       1 → "falta 1 dia", 0 → "vence hoje", -3 → "venceu há 3 dias"
    */
    static String textoPrazo(long diasRestantes) {
        if (diasRestantes > 1) {
            return "faltam " + diasRestantes + " dias";
        }
        if (diasRestantes == 1) {
            return "falta 1 dia";
        }
        if (diasRestantes == 0) {
            return "vence hoje";
        }
        if (diasRestantes == -1) {
            return "venceu há 1 dia";
        }
        return "venceu há " + (-diasRestantes) + " dias";
    }

    /*
       Percentual com uma casa decimal, usado na largura das barras. Exemplo: 5 de 9 = 55.6.
       Se a referência for zero, a barra fica vazia (0)
    */
    static double percentual(long valor, long referencia) {
        if (referencia <= 0) {
            return 0;
        }
        return Math.round(valor * 1000.0 / referencia) / 10.0;
    }

    // Estágios antigos podem não ter a data prevista gravada, então calculamos como o EstagioService faz
    private static LocalDate dataPrevista(Estagio estagio) {
        if (estagio.getDataPrevistaConclusao() != null) {
            return estagio.getDataPrevistaConclusao();
        }
        return CalculadoraCargaHoraria.calcularDataPrevista(estagio.getDataInicio(),
                estagio.getCargaHorariaNecessaria(), estagio.getJornadaSemanal());
    }

    // Sem curso escolhido, entram todos os cursos do coordenador; com curso, só os estágios dele
    private static boolean doCursoEscolhido(Estagio estagio, Long cursoId) {
        return cursoId == null || cursoId.equals(estagio.getMatricula().getTurma().getCurso().getId());
    }

    // Transforma uma lista com repetições em contagem. Exemplo: [3, 3, 5] vira {3: 2, 5: 1}
    private static Map<Long, Long> contarRepeticoes(List<Long> ids) {
        return ids.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }

    // Linha do gráfico 1 enquanto estamos somando
    private static final class LinhaSituacao {
        private final Turma turma;
        private long semOrientador;
        private long aguardandoValidacao;
        private long emAndamento;

        private LinhaSituacao(Turma turma) {
            this.turma = turma;
        }

        private long total() {
            return semOrientador + aguardandoValidacao + emAndamento;
        }
    }

    // Linha do gráfico 2 enquanto estamos somando
    private static final class LinhaCorrecao {
        private final Turma turma;
        private final String orientador;
        private long quantidade;

        private LinhaCorrecao(Turma turma, String orientador) {
            this.turma = turma;
            this.orientador = orientador;
        }
    }

    // Linha do gráfico 3 enquanto estamos somando. Todos os estágios da turma têm a mesma data, então a faixa é uma só
    private static final class LinhaPrazo {
        private final Turma turma;
        private final long diasRestantes;
        private final String faixa;
        private long quantidade;

        private LinhaPrazo(Turma turma, long diasRestantes, String faixa) {
            this.turma = turma;
            this.diasRestantes = diasRestantes;
            this.faixa = faixa;
        }
    }
}
