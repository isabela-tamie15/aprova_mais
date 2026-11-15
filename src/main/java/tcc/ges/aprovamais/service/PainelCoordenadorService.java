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

/*
   Dashboard do coordenador. Só lê estágios, turmas e entregas e monta os 6 cartões e os 3
   gráficos por turma. As regras de prazo e de pendência são as da CalculadoraCargaHoraria e
   da PendenciasEstagio, então aluno, orientador e coordenador veem os mesmos números
*/
@Service
@RequiredArgsConstructor
public class PainelCoordenadorService {

    private static final Logger log = LoggerFactory.getLogger(PainelCoordenadorService.class);

    // Status que entram no painel, PENDENTE nos cartões 1 e 2 e ATIVO nos cartões 3, 4 e 6
    private static final Set<StatusEstagio> STATUS_DO_PAINEL = EnumSet.of(StatusEstagio.PENDENTE, StatusEstagio.ATIVO);

    private final PainelCoordenadorRepository painelCoordenadorRepository;
    private final CursoService cursoService;

    /*
       Monta o painel do coordenador logado. Sem o cursoId entram todos os cursos dele, e com o cursoId
       só aquele curso. O curso de outro coordenador recebe 404 do CursoService
    */
    @Transactional(readOnly = true)
    public PainelCoordenadorResponse montarPainel(String emailCoordenador, Long cursoId) {
        if (cursoId != null) {
            cursoService.buscarCursoDoCoordenador(cursoId, emailCoordenador);
        }
        LocalDate hoje = LocalDate.now();

        /*
           Passo 1, as consultas do painel. Estágios PENDENTE e ATIVO dos cursos do coordenador,
           só do curso escolhido quando há filtro
        */
        List<Estagio> estagiosDosCursos = painelCoordenadorRepository
                .listarEstagiosDosCursos(emailCoordenador, STATUS_DO_PAINEL);
        List<Estagio> estagios = new ArrayList<>();
        for (Estagio estagio : estagiosDosCursos) {
            if (doCursoEscolhido(estagio, cursoId)) {
                estagios.add(estagio);
            }
        }

        Map<Long, Long> tarefasPorTipoEstagio = contarRepeticoes(
                painelCoordenadorRepository.listarTipoEstagioDasTarefas(emailCoordenador));
        Map<Long, Long> aprovadasPorEstagio = contarRepeticoes(
                painelCoordenadorRepository.listarEstagioDasEntregas(
                        emailCoordenador, StatusEstagio.ATIVO, StatusEntrega.APROVADA));

        // Entregas esperando correção nos cursos do coordenador, com o mesmo filtro de curso
        List<Entrega> entregasDosCursos = painelCoordenadorRepository
                .listarEntregasDosCursos(emailCoordenador, CorrecaoService.AGUARDANDO_ANALISE);
        List<Entrega> entregasAguardando = new ArrayList<>();
        for (Entrega entrega : entregasDosCursos) {
            if (doCursoEscolhido(entrega.getEstagio(), cursoId)) {
                entregasAguardando.add(entrega);
            }
        }

        // Passo 2, cada estágio soma nos cartões e nas linhas da sua turma
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

            // Gráfico 1, cada turma é uma barra, criada na primeira vez que a turma aparece
            LinhaSituacao situacao = situacaoPorTurma.get(turma.getId());   // procura a barra da turma
            if (situacao == null) {                                         // primeiro estágio dessa turma? a barra não existe
                situacao = new LinhaSituacao(turma);                        //   cria a barra, com tudo zerado
                situacaoPorTurma.put(turma.getId(), situacao);              //   guarda a barra no mapa, pelo id da turma
            }

            // Cartões 1 e 2. O estágio PENDENTE fica sem orientador quando a turma não tem orientador
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

            // Cartão 4. Sem a data de encerramento, o aviso de prazo fica desligado para os alunos da turma
            LocalDate dataEncerramento = turma.getDataEncerramentoEntregas();
            Long diasRestantes = CalculadoraCargaHoraria.calcularDiasRestantes(dataEncerramento, hoje);
            if (diasRestantes == null) {
                turmasSemData.add(turma.getId());
                continue;
            }

            // Reta final, a mesma faixa do aviso de prazo, de 30 dias antes até depois da data
            String faixa = CalculadoraCargaHoraria.definirAvisoPrazo(diasRestantes);
            if (faixa == null) {
                continue;
            }

            // Cartão 6, com as duas pendências, cada uma pela sua regra da PendenciasEstagio
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

            // Gráfico 3, cada turma na reta final é uma barra, criada na primeira vez que a turma aparece
            LinhaPrazo linhaPrazo = prazoPorTurma.get(turma.getId());          // procura a barra da turma
            if (linhaPrazo == null) {                                          // primeiro estágio dessa turma na reta final?
                linhaPrazo = new LinhaPrazo(turma, diasRestantes, faixa);      //   cria a barra, com quantidade 0
                prazoPorTurma.put(turma.getId(), linhaPrazo);                  //   guarda a barra no mapa
            }
            linhaPrazo.quantidade++;                                           // soma 1 estágio nessa barra
        }

        // Cartão 5 e gráfico 2, entregas esperando correção por turma e orientador do estágio
        Map<String, LinhaCorrecao> correcaoPorTurma = new HashMap<>();
        for (Entrega entrega : entregasAguardando) {
            Estagio estagio = entrega.getEstagio();
            Turma turma = estagio.getMatricula().getTurma();

            // Estágio sem orientador fica com o id nulo e o nome "Sem orientador"
            Long orientadorId = estagio.getOrientador() != null ? estagio.getOrientador().getId() : null;
            String orientador = estagio.getOrientador() != null ? estagio.getOrientador().getNome() : "Sem orientador";

            // Gráfico 2, cada dupla de turma e orientador é uma barra, criada na primeira vez que aparece
            String chave = turma.getId() + "-" + orientadorId;                 // chave = "3-7" (turma 3 com orientador 7)
            LinhaCorrecao linhaCorrecao = correcaoPorTurma.get(chave);         // procura a barra "3-7"
            if (linhaCorrecao == null) {                                       // primeira entrega dessa dupla? a barra não existe
                linhaCorrecao = new LinhaCorrecao(turma, orientador);          //   cria a barra, com quantidade 0
                correcaoPorTurma.put(chave, linhaCorrecao);                    //   guarda a barra no mapa
            }
            linhaCorrecao.quantidade++;                                        // soma 1 entrega nessa barra
        }

        log.debug("Painel do coordenador montado: {} estágios e {} entregas aguardando correção",
                estagios.size(), entregasAguardando.size());

        // Passo 3, a resposta com as listas ordenadas e as barras calculadas
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

    // Gráfico 1, do maior total para o menor. A maior turma fica com a barra cheia
    private List<PainelCoordenadorResponse.SituacaoTurma> montarSituacao(Collection<LinhaSituacao> linhas) {
        // Ordenamos as turmas com a regra do método compararLinhasDeSituacao (mais abaixo)
        List<LinhaSituacao> ordenadas = new ArrayList<>(linhas);
        ordenadas.sort((primeira, segunda) -> compararLinhasDeSituacao(primeira, segunda));

        // A lista já está ordenada, então a primeira turma é a maior e fica com a barra cheia
        long maior = 0;
        if (!ordenadas.isEmpty()) {
            maior = ordenadas.get(0).total();
        }

        // Transforma cada linha somada na resposta que vai para a tela
        List<PainelCoordenadorResponse.SituacaoTurma> respostas = new ArrayList<>();
        for (LinhaSituacao linha : ordenadas) {
            respostas.add(PainelCoordenadorResponse.SituacaoTurma.builder()
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
                    .build());
        }
        return respostas;
    }

    // Gráfico 2, do maior número de tarefas para o menor
    private List<PainelCoordenadorResponse.CorrecaoTurma> montarCorrecao(Collection<LinhaCorrecao> linhas) {
        // Ordenamos as barras com a regra do método compararLinhasDeCorrecao (mais abaixo)
        List<LinhaCorrecao> ordenadas = new ArrayList<>(linhas);
        ordenadas.sort((primeira, segunda) -> compararLinhasDeCorrecao(primeira, segunda));

        // A lista já está ordenada, então a primeira barra é a maior e fica cheia
        long maior = 0;
        if (!ordenadas.isEmpty()) {
            maior = ordenadas.get(0).quantidade;
        }

        // Transforma cada linha somada na resposta que vai para a tela
        List<PainelCoordenadorResponse.CorrecaoTurma> respostas = new ArrayList<>();
        for (LinhaCorrecao linha : ordenadas) {
            respostas.add(PainelCoordenadorResponse.CorrecaoTurma.builder()
                    .turmaId(linha.turma.getId())
                    .turma(linha.turma.getIdentificador())
                    .semestre(linha.turma.getSemestre())
                    .curso(linha.turma.getCurso().getNome())
                    .orientador(linha.orientador)
                    .quantidade(linha.quantidade)
                    .percentualBarra(percentual(linha.quantidade, maior))
                    .build());
        }
        return respostas;
    }

    // Gráfico 3, do maior número de estágios para o menor, e no empate a turma com mais dias vem antes
    private List<PainelCoordenadorResponse.PrazoTurma> montarPrazo(Collection<LinhaPrazo> linhas) {
        // Ordenamos as turmas com a regra do método compararLinhasDePrazo (mais abaixo)
        List<LinhaPrazo> ordenadas = new ArrayList<>(linhas);
        ordenadas.sort((primeira, segunda) -> compararLinhasDePrazo(primeira, segunda));

        // A lista já está ordenada, então a primeira turma é a maior e fica com a barra cheia
        long maior = 0;
        if (!ordenadas.isEmpty()) {
            maior = ordenadas.get(0).quantidade;
        }

        // Transforma cada linha somada na resposta que vai para a tela
        List<PainelCoordenadorResponse.PrazoTurma> respostas = new ArrayList<>();
        for (LinhaPrazo linha : ordenadas) {
            respostas.add(PainelCoordenadorResponse.PrazoTurma.builder()
                    .turmaId(linha.turma.getId())
                    .turma(linha.turma.getIdentificador())
                    .semestre(linha.turma.getSemestre())
                    .curso(linha.turma.getCurso().getNome())
                    .quantidade(linha.quantidade)
                    .diasRestantes(linha.diasRestantes)
                    .faixa(linha.faixa)
                    .textoPrazo(textoPrazo(linha.diasRestantes))
                    .percentualBarra(percentual(linha.quantidade, maior))
                    .build());
        }
        return respostas;
    }

    /*
       Ordenação dos gráficos. Cada método compara dois itens e devolve um número negativo quando o
       primeiro vem antes, positivo quando o segundo vem antes e zero no empate
    */

    // Gráfico 1. A turma com mais estágios aparece primeiro, e no empate vale a ordem das turmas
    private static int compararLinhasDeSituacao(LinhaSituacao primeira, LinhaSituacao segunda) {
        // 1º critério, total de estágios, do maior para o menor
        if (primeira.total() != segunda.total()) {
            return Long.compare(segunda.total(), primeira.total());
        }
        // 2º critério, semestre mais recente, depois identificador, depois curso
        return compararTurmas(primeira.turma, segunda.turma);
    }

    /*
       Gráfico 2. Quem tem mais entregas esperando aparece primeiro. No empate vale a ordem das turmas
       e depois o nome do orientador
    */
    private static int compararLinhasDeCorrecao(LinhaCorrecao primeira, LinhaCorrecao segunda) {
        // 1º critério, quantidade, da maior para a menor
        if (primeira.quantidade != segunda.quantidade) {
            return Long.compare(segunda.quantidade, primeira.quantidade);
        }
        // 2º critério, semestre mais recente, depois identificador, depois curso
        int resultadoDaTurma = compararTurmas(primeira.turma, segunda.turma);
        if (resultadoDaTurma != 0) {
            return resultadoDaTurma;
        }
        // 3º critério, nome do orientador em ordem alfabética
        return primeira.orientador.compareTo(segunda.orientador);
    }

    /*
       Gráfico 3. A turma com mais estágios na reta final aparece primeiro. No empate vem a turma
       com mais dias até o prazo e depois a ordem das turmas
    */
    private static int compararLinhasDePrazo(LinhaPrazo primeira, LinhaPrazo segunda) {
        // 1º critério, quantidade de estágios, da maior para a menor
        if (primeira.quantidade != segunda.quantidade) {
            return Long.compare(segunda.quantidade, primeira.quantidade);
        }
        // 2º critério, dias até o prazo, do maior para o menor
        if (primeira.diasRestantes != segunda.diasRestantes) {
            return Long.compare(segunda.diasRestantes, primeira.diasRestantes);
        }
        // 3º critério, semestre mais recente, depois identificador, depois curso
        return compararTurmas(primeira.turma, segunda.turma);
    }

    // Ordem das turmas no empate. Semestre mais recente primeiro (2026/2 antes de 2026/1), depois o identificador e o curso
    private static int compararTurmas(Turma primeira, Turma segunda) {
        int resultadoDoSemestre = segunda.getSemestre().compareTo(primeira.getSemestre());
        if (resultadoDoSemestre != 0) {
            return resultadoDoSemestre;
        }
        int resultadoDoIdentificador = primeira.getIdentificador().compareTo(segunda.getIdentificador());
        if (resultadoDoIdentificador != 0) {
            return resultadoDoIdentificador;
        }
        return primeira.getCurso().getNome().compareTo(segunda.getCurso().getNome());
    }

    /*
       Frase do prazo embaixo da turma no gráfico 3. Por exemplo, 15 vira "faltam 15 dias", 1 vira
       "falta 1 dia", 0 vira "vence hoje" e -3 vira "venceu há 3 dias"
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

    // Percentual com uma casa decimal para a largura das barras, como 5 de 9 que dá 55.6. Com total zero a barra fica vazia
    static double percentual(long quantidade, long total) {
        if (total <= 0) {
            return 0;
        }
        return Math.round(quantidade * 1000.0 / total) / 10.0;
    }

    // Estágios antigos podem não ter a data prevista gravada, então calculamos como o EstagioService faz
    private static LocalDate dataPrevista(Estagio estagio) {
        if (estagio.getDataPrevistaConclusao() != null) {
            return estagio.getDataPrevistaConclusao();
        }
        return CalculadoraCargaHoraria.calcularDataPrevista(estagio.getDataInicio(),
                estagio.getCargaHorariaNecessaria(), estagio.getJornadaSemanal());
    }

    // Sem curso escolhido entram todos os cursos do coordenador, e com curso só os estágios dele
    private static boolean doCursoEscolhido(Estagio estagio, Long cursoId) {
        return cursoId == null || cursoId.equals(estagio.getMatricula().getTurma().getCurso().getId());
    }

    /*
       Conta quantas vezes cada id aparece na lista. Serve para saber quantas tarefas tem a trilha
       de cada tipo de estágio e quantas entregas aprovadas tem cada estágio, porque o banco devolve
       o id uma vez por item. Por exemplo, [3, 3, 5] vira {3: 2, 5: 1}
    */
    private static Map<Long, Long> contarRepeticoes(List<Long> ids) {
        Map<Long, Long> quantidadePorId = new HashMap<>();
        for (Long id : ids) {
            if (quantidadePorId.containsKey(id)) {
                long quantidadeAnterior = quantidadePorId.get(id);      // já apareceu antes e soma 1
                quantidadePorId.put(id, quantidadeAnterior + 1);        //   e somamos 1
            } else {
                quantidadePorId.put(id, 1L);                            // primeira vez que aparece, começa com 1
            }
        }
        return quantidadePorId;
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
