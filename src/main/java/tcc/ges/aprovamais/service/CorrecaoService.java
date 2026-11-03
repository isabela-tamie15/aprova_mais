package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.dto.*;
import tcc.ges.aprovamais.entity.*;
import tcc.ges.aprovamais.entity.enums.StatusEntrega;
import tcc.ges.aprovamais.exception.DadoInvalidoException;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.EntregaRepository;
import tcc.ges.aprovamais.repository.EntregaVersaoRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;

/*
   Painel de correção do orientador e avaliação das entregas
   O orientador só enxerga as entregas dos estágios em que ele é o orientador
   O checklist de critérios entra na avaliação depois
*/
@Service
@RequiredArgsConstructor
public class CorrecaoService {

    private static final Logger log = LoggerFactory.getLogger(CorrecaoService.class);

    // Entregas que aguardam análise
    public static final Set<StatusEntrega> AGUARDANDO_ANALISE = EnumSet.of(StatusEntrega.ENVIADA, StatusEntrega.EM_REVISAO);

    public static final int TAMANHO_MAXIMO_PAGINA = 50;

    // Datas do filtro de período são do calendário de SP, como as do resto do sistema
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private final EntregaRepository entregaRepository;
    private final EntregaVersaoRepository entregaVersaoRepository;
    private final EntregaService entregaService;
    private final AuditoriaService auditoriaService;
    private final EmailService emailService;

    @Value("${app.url}")
    private String appUrl;

    // Filtros combináveis do painel, eles são opcionais
    public record FiltroPainel(Long alunoId, Long tarefaId, StatusEntrega status,
                               LocalDate de, LocalDate ate, int pagina, int tamanho) {
    }

    // Linha do painel já com a última versão, que dá a data de envio usada na ordem e no período
    private record Linha(Entrega entrega, EntregaVersao ultimaVersao) {
    }

    /*
       Entregas dos alunos do orientador, da mais antiga para a mais recente.
       Sem filtro de status, mostra as que aguardam análise, com o filtro, qualquer status (inclusive as já aprovadas ou devolvidas)
    */
    @Transactional(readOnly = true)
    public PainelCorrecaoResponse listar(String emailOrientador, FiltroPainel filtro) {
        Set<StatusEntrega> status = filtro.status() == null ? AGUARDANDO_ANALISE : EnumSet.of(filtro.status());

        List<Linha> todas = entregaRepository.findByEstagioOrientadorEmailAndStatusIn(emailOrientador, status).stream()
                .map(e -> new Linha(e, entregaVersaoRepository.findFirstByEntregaIdOrderByNumeroDesc(e.getId()).orElse(null)))
                .filter(l -> l.ultimaVersao() != null)
                .sorted(Comparator.comparing((Linha l) -> l.ultimaVersao().getEnviadaEm())
                        .thenComparing(l -> l.entrega().getId()))
                .toList();

        // Opções dos filtros saem das entregas do próprio orientador, então nunca mostram aluno de outro
        List<OpcaoFiltroResponse> alunos = opcoes(todas, l -> l.entrega().getEstagio().getMatricula().getAluno().getId(),
                l -> l.entrega().getEstagio().getMatricula().getAluno().getNome());
        List<OpcaoFiltroResponse> tarefas = opcoes(todas, l -> l.entrega().getTarefa().getId(),
                l -> l.entrega().getTarefa().getNome());

        List<Linha> filtradas = todas.stream()
                .filter(l -> filtro.alunoId() == null
                        || filtro.alunoId().equals(l.entrega().getEstagio().getMatricula().getAluno().getId()))
                .filter(l -> filtro.tarefaId() == null || filtro.tarefaId().equals(l.entrega().getTarefa().getId()))
                .filter(l -> dentroDoPeriodo(l.ultimaVersao().getEnviadaEm(), filtro.de(), filtro.ate()))
                .toList();

        int tamanho = Math.clamp(filtro.tamanho(), 1, TAMANHO_MAXIMO_PAGINA);
        int totalPaginas = Math.max(1, (int) Math.ceil(filtradas.size() / (double) tamanho));
        int pagina = Math.clamp(filtro.pagina(), 0, totalPaginas - 1);

        List<ItemPainelCorrecaoResponse> itens = filtradas.stream()
                .skip((long) pagina * tamanho)
                .limit(tamanho)
                .map(this::paraItem)
                .toList();

        long totalAguardando = filtro.status() == null
                ? todas.size()
                : entregaRepository.findByEstagioOrientadorEmailAndStatusIn(emailOrientador, AGUARDANDO_ANALISE).size();

        return PainelCorrecaoResponse.builder()
                .totalAguardando(totalAguardando)
                .totalFiltrado(filtradas.size())
                .pagina(pagina)
                .tamanho(tamanho)
                .totalPaginas(totalPaginas)
                .itens(itens)
                .alunos(alunos)
                .tarefas(tarefas)
                .build();
    }

    /*
       O orientador abre a entrega. Se ela estava ENVIADA, passa a EM_REVISAO, para o aluno saber que já está sendo analisada.
       Volta o histórico completo, com o feedback das versões anteriores
    */
    @Transactional
    public EntregaDetalheResponse abrir(String emailOrientador, Long entregaId) {
        Entrega entrega = buscarEntregaDoOrientador(entregaId, emailOrientador);
        if (entrega.getStatus() == StatusEntrega.ENVIADA) {
            entrega.setStatus(StatusEntrega.EM_REVISAO);
            entregaRepository.save(entrega);
            log.info("[CORREÇÃO] Entrega {} em revisão por {}", entregaId, emailOrientador);
        }
        return entregaService.montarDetalhe(entrega.getTarefa(), entrega);
    }


    //Aprova ou devolve a última versão, gravando o feedback nela
    @Transactional
    public EntregaDetalheResponse avaliar(String emailOrientador, Long entregaId,
                                          AvaliacaoEntregaRequest request, String ipOrigem) {
        Entrega entrega = buscarEntregaDoOrientador(entregaId, emailOrientador);
        if (!AGUARDANDO_ANALISE.contains(entrega.getStatus())) {
            throw new IllegalStateException("Esta entrega já foi avaliada.");
        }
        EntregaVersao versao = entregaVersaoRepository.findFirstByEntregaIdOrderByNumeroDesc(entrega.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Entrega não encontrada."));

        boolean aprovar = request.getDecisao() == AvaliacaoEntregaRequest.Decisao.APROVAR;
        String feedback = request.getFeedback() == null || request.getFeedback().isBlank()
                ? null : request.getFeedback().trim();

        if (!aprovar && feedback == null) {
            throw new DadoInvalidoException("Escreva o feedback para o aluno saber o que corrigir.");
        }

        versao.setResultado(aprovar ? StatusEntrega.APROVADA : StatusEntrega.DEVOLVIDA);
        versao.setFeedback(feedback);
        versao.setAvaliador(entrega.getEstagio().getOrientador());
        versao.setAvaliadaEm(OffsetDateTime.now());
        entregaVersaoRepository.save(versao);

        entrega.setStatus(versao.getResultado());
        entregaRepository.save(entrega);

        Aluno aluno = entrega.getEstagio().getMatricula().getAluno();
        auditoriaService.registrar(entrega.getEstagio().getOrientador(),
                aprovar ? "ENTREGA_APROVADA" : "ENTREGA_DEVOLVIDA",
                "Entrega id=" + entrega.getId() + ", versão " + versao.getNumero(),
                ipOrigem, true);

        emailService.enviarResultadoEntrega(aluno.getEmail(), aluno.getNome(), entrega.getTarefa().getNome(),
                aprovar, appUrl + "/aluno/trilha");
        log.info("[CORREÇÃO] Entrega {} versão {} {} por {}", entregaId, versao.getNumero(),
                aprovar ? "aprovada" : "devolvida", emailOrientador);

        return entregaService.montarDetalhe(entrega.getTarefa(), entrega);
    }

    private ItemPainelCorrecaoResponse paraItem(Linha linha) {
        Entrega entrega = linha.entrega();
        Aluno aluno = entrega.getEstagio().getMatricula().getAluno();
        return ItemPainelCorrecaoResponse.builder()
                .entregaId(entrega.getId())
                .alunoId(aluno.getId())
                .nomeAluno(aluno.getNome())
                .tarefaId(entrega.getTarefa().getId())
                .nomeTarefa(entrega.getTarefa().getNome())
                .status(entrega.getStatus().name())
                .numeroVersao(linha.ultimaVersao().getNumero())
                .enviadaEm(linha.ultimaVersao().getEnviadaEm())
                .build();
    }

    private static List<OpcaoFiltroResponse> opcoes(List<Linha> linhas, Function<Linha, Long> id,
                                                    Function<Linha, String> nome) {
        Map<Long, String> mapa = new LinkedHashMap<>();
        linhas.forEach(l -> mapa.putIfAbsent(id.apply(l), nome.apply(l)));
        return mapa.entrySet().stream()
                .map(e -> new OpcaoFiltroResponse(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(OpcaoFiltroResponse::nome, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private static boolean dentroDoPeriodo(OffsetDateTime enviadaEm, LocalDate de, LocalDate ate) {
        LocalDate dia = enviadaEm.atZoneSameInstant(FUSO).toLocalDate();
        return (de == null || !dia.isBefore(de)) && (ate == null || !dia.isAfter(ate));
    }

    private Entrega buscarEntregaDoOrientador(Long entregaId, String emailOrientador) {
        return entregaRepository.findByIdAndEstagioOrientadorEmail(entregaId, emailOrientador)
                .orElseThrow(() -> new ResourceNotFoundException("Entrega não encontrada."));
    }
}
