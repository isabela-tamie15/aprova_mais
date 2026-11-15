package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.dto.*;
import tcc.ges.aprovamais.entity.*;
import tcc.ges.aprovamais.entity.enums.StatusEntrega;
import tcc.ges.aprovamais.exception.DadoInvalidoException;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.external.EmailService;
import tcc.ges.aprovamais.repository.EntregaRepository;
import tcc.ges.aprovamais.repository.EntregaVersaoRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

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

    // Resultados que contam como correção feita
    public static final Set<StatusEntrega> CORRIGIDAS = EnumSet.of(StatusEntrega.APROVADA, StatusEntrega.DEVOLVIDA);

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

    /*
       Entregas dos alunos do orientador, da mais antiga para a mais recente.
       Sem filtro de status, mostra as que aguardam análise, com o filtro, qualquer status (inclusive as já aprovadas ou devolvidas).
       O filtro, a ordem e a paginação são feitos no banco, que devolve só a página pedida (AUD-10)
    */
    @Transactional(readOnly = true)
    public PainelCorrecaoResponse listar(String emailOrientador, FiltroPainel filtro) {
        Set<StatusEntrega> status = filtro.status() == null ? AGUARDANDO_ANALISE : EnumSet.of(filtro.status());

        // O tamanho da página fica entre 1 e 50, mesmo que alguém mude o valor na URL
        int tamanho = filtro.tamanho();
        if (tamanho < 1) {
            tamanho = 1;
        }
        if (tamanho > TAMANHO_MAXIMO_PAGINA) {
            tamanho = TAMANHO_MAXIMO_PAGINA;
        }

        // O período da tela é em dias do calendário de SP: vai do início do dia "de" até antes do início do dia seguinte ao "ate"
        OffsetDateTime inicio = null;
        if (filtro.de() != null) {
            inicio = filtro.de().atStartOfDay(FUSO).toOffsetDateTime();
        }
        OffsetDateTime fim = null;
        if (filtro.ate() != null) {
            fim = filtro.ate().plusDays(1).atStartOfDay(FUSO).toOffsetDateTime();
        }

        Page<EntregaVersao> paginaDoPainel = entregaVersaoRepository.buscarPainelDeCorrecao(emailOrientador, status,
                filtro.alunoId(), filtro.tarefaId(), inicio, fim, PageRequest.of(Math.max(filtro.pagina(), 0), tamanho));

        // Página além da última volta para a última, como antes
        if (paginaDoPainel.getTotalPages() > 0 && filtro.pagina() >= paginaDoPainel.getTotalPages()) {
            paginaDoPainel = entregaVersaoRepository.buscarPainelDeCorrecao(emailOrientador, status,
                    filtro.alunoId(), filtro.tarefaId(), inicio, fim,
                    PageRequest.of(paginaDoPainel.getTotalPages() - 1, tamanho));
        }

        List<ItemPainelCorrecaoResponse> itens = new ArrayList<>();
        for (EntregaVersao ultimaVersao : paginaDoPainel.getContent()) {
            itens.add(paraItem(ultimaVersao));
        }

        // Opções dos filtros saem das entregas do próprio orientador, então nunca mostram aluno de outro
        List<OpcaoFiltroResponse> alunos = emOrdemAlfabetica(entregaRepository.listarAlunosDoPainel(emailOrientador, status));
        List<OpcaoFiltroResponse> tarefas = emOrdemAlfabetica(entregaRepository.listarTarefasDoPainel(emailOrientador, status));

        long totalAguardando = entregaRepository.countByEstagioOrientadorEmailAndStatusIn(emailOrientador, AGUARDANDO_ANALISE);

        return PainelCorrecaoResponse.builder()
                .totalAguardando(totalAguardando)
                .totalFiltrado(paginaDoPainel.getTotalElements())
                .pagina(paginaDoPainel.getNumber())
                .tamanho(tamanho)
                .totalPaginas(Math.max(1, paginaDoPainel.getTotalPages()))
                .itens(itens)
                .alunos(alunos)
                .tarefas(tarefas)
                .build();
    }

    /*
       Histórico de correções do orientador: cada versão que ele aprovou ou devolveu é uma linha,
       da avaliação mais recente para a mais antiga. Uma tarefa devolvida e depois aprovada aparece duas vezes, porque foram duas correções
    */
    @Transactional(readOnly = true)
    public HistoricoCorrecoesResponse listarHistorico(String emailOrientador, int pagina, int tamanho) {
        // O tamanho da página fica entre 1 e 50, mesmo que alguém mude o valor na URL
        int tamanhoPagina = tamanho;
        if (tamanhoPagina < 1) {
            tamanhoPagina = 1;
        }
        if (tamanhoPagina > TAMANHO_MAXIMO_PAGINA) {
            tamanhoPagina = TAMANHO_MAXIMO_PAGINA;
        }
        Sort ordem = Sort.by(Sort.Direction.DESC, "avaliadaEm").and(Sort.by(Sort.Direction.DESC, "id"));

        Page<EntregaVersao> paginaDeVersoesCorrigidas = entregaVersaoRepository.findByAvaliadorEmailAndResultadoIn(
                emailOrientador, CORRIGIDAS, PageRequest.of(Math.max(pagina, 0), tamanhoPagina, ordem));

        // Página além da última volta para a última, como no painel de correção
        if (paginaDeVersoesCorrigidas.getTotalPages() > 0 && pagina >= paginaDeVersoesCorrigidas.getTotalPages()) {
            paginaDeVersoesCorrigidas = entregaVersaoRepository.findByAvaliadorEmailAndResultadoIn(
                    emailOrientador, CORRIGIDAS, PageRequest.of(paginaDeVersoesCorrigidas.getTotalPages() - 1, tamanhoPagina, ordem));
        }

        // Transforma cada versão corrigida na linha que vai para a tela do histórico
        List<ItemHistoricoCorrecaoResponse> itens = new ArrayList<>();
        for (EntregaVersao versao : paginaDeVersoesCorrigidas.getContent()) {
            itens.add(paraItemHistorico(versao));
        }

        return HistoricoCorrecoesResponse.builder()
                .totalCorrecoes(paginaDeVersoesCorrigidas.getTotalElements())
                .pagina(paginaDeVersoesCorrigidas.getNumber())
                .tamanho(tamanhoPagina)
                .totalPaginas(Math.max(1, paginaDeVersoesCorrigidas.getTotalPages()))
                .itens(itens)
                .build();
    }

    private ItemHistoricoCorrecaoResponse paraItemHistorico(EntregaVersao versao) {
        Entrega entrega = versao.getEntrega();
        return ItemHistoricoCorrecaoResponse.builder()
                .entregaId(entrega.getId())
                .nomeAluno(entrega.getEstagio().getMatricula().getAluno().getNome())
                .nomeTarefa(entrega.getTarefa().getNome())
                .numeroVersao(versao.getNumero())
                .resultado(versao.getResultado().name())
                .feedback(versao.getFeedback())
                .avaliadaEm(versao.getAvaliadaEm())
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

    private ItemPainelCorrecaoResponse paraItem(EntregaVersao ultimaVersao) {
        Entrega entrega = ultimaVersao.getEntrega();
        Aluno aluno = entrega.getEstagio().getMatricula().getAluno();
        return ItemPainelCorrecaoResponse.builder()
                .entregaId(entrega.getId())
                .alunoId(aluno.getId())
                .nomeAluno(aluno.getNome())
                .tarefaId(entrega.getTarefa().getId())
                .nomeTarefa(entrega.getTarefa().getNome())
                .status(entrega.getStatus().name())
                .numeroVersao(ultimaVersao.getNumero())
                .enviadaEm(ultimaVersao.getEnviadaEm())
                .build();
    }

    // Opções do filtro em ordem alfabética, sem diferenciar maiúscula e minúscula
    private static List<OpcaoFiltroResponse> emOrdemAlfabetica(List<OpcaoFiltroResponse> opcoesDoBanco) {
        List<OpcaoFiltroResponse> opcoes = new ArrayList<>(opcoesDoBanco);
        opcoes.sort((primeira, segunda) -> String.CASE_INSENSITIVE_ORDER.compare(primeira.nome(), segunda.nome()));
        return opcoes;
    }

    private Entrega buscarEntregaDoOrientador(Long entregaId, String emailOrientador) {
        return entregaRepository.findByIdAndEstagioOrientadorEmail(entregaId, emailOrientador)
                .orElseThrow(() -> new ResourceNotFoundException("Entrega não encontrada."));
    }
}
