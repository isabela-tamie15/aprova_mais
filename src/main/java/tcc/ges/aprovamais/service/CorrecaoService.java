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

        // Cada entrega do orientador vira uma linha com a sua última versão. Entrega sem versão não tem o que corrigir
        List<Entrega> entregasDoOrientador = entregaRepository.findByEstagioOrientadorEmailAndStatusIn(emailOrientador, status);
        List<Linha> todas = new ArrayList<>();
        for (Entrega entrega : entregasDoOrientador) {
            Optional<EntregaVersao> ultimaVersao = entregaVersaoRepository.findFirstByEntregaIdOrderByNumeroDesc(entrega.getId());
            if (ultimaVersao.isPresent()) {
                todas.add(new Linha(entrega, ultimaVersao.get()));
            }
        }
        // Da entrega enviada há mais tempo para a mais recente (regra no método compararPorDataDeEnvio)
        todas.sort((primeira, segunda) -> compararPorDataDeEnvio(primeira, segunda));

        // Opções dos filtros saem das entregas do próprio orientador, então nunca mostram aluno de outro
        List<OpcaoFiltroResponse> alunos = listarAlunosDasEntregas(todas);
        List<OpcaoFiltroResponse> tarefas = listarTarefasDasEntregas(todas);

        // Aplica os filtros da tela. Filtro vazio (null) deixa passar todas as entregas
        List<Linha> filtradas = new ArrayList<>();
        for (Linha linha : todas) {
            Long idDoAluno = linha.entrega().getEstagio().getMatricula().getAluno().getId();
            Long idDaTarefa = linha.entrega().getTarefa().getId();

            boolean passaNoFiltroDeAluno = filtro.alunoId() == null || filtro.alunoId().equals(idDoAluno);
            boolean passaNoFiltroDeTarefa = filtro.tarefaId() == null || filtro.tarefaId().equals(idDaTarefa);
            boolean passaNoFiltroDePeriodo = dentroDoPeriodo(linha.ultimaVersao().getEnviadaEm(), filtro.de(), filtro.ate());

            if (passaNoFiltroDeAluno && passaNoFiltroDeTarefa && passaNoFiltroDePeriodo) {
                filtradas.add(linha);
            }
        }

        // O tamanho da página fica entre 1 e 50, mesmo que alguém mude o valor na URL
        int tamanho = filtro.tamanho();
        if (tamanho < 1) {
            tamanho = 1;
        }
        if (tamanho > TAMANHO_MAXIMO_PAGINA) {
            tamanho = TAMANHO_MAXIMO_PAGINA;
        }

        int totalPaginas = Math.max(1, (int) Math.ceil(filtradas.size() / (double) tamanho));

        // A página pedida fica entre a primeira (0) e a última que existe
        int pagina = filtro.pagina();
        if (pagina < 0) {
            pagina = 0;
        }
        if (pagina > totalPaginas - 1) {
            pagina = totalPaginas - 1;
        }

        // Pega só as entregas da página pedida. Exemplo: página 1 com tamanho 20 vai da posição 20 até a 39
        int primeiraPosicao = pagina * tamanho;
        int posicaoDepoisDaUltima = Math.min(primeiraPosicao + tamanho, filtradas.size());
        List<ItemPainelCorrecaoResponse> itens = new ArrayList<>();
        for (int posicao = primeiraPosicao; posicao < posicaoDepoisDaUltima; posicao++) {
            itens.add(paraItem(filtradas.get(posicao)));
        }

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

    // Ordem do painel: a entrega enviada há mais tempo vem primeiro; se o horário empatar, a de menor id
    private static int compararPorDataDeEnvio(Linha primeira, Linha segunda) {
        int resultadoDaData = primeira.ultimaVersao().getEnviadaEm().compareTo(segunda.ultimaVersao().getEnviadaEm());
        if (resultadoDaData != 0) {
            return resultadoDaData;
        }
        return primeira.entrega().getId().compareTo(segunda.entrega().getId());
    }

    // Alunos que aparecem nas entregas, cada um uma vez só, para o filtro "Aluno" da tela
    private static List<OpcaoFiltroResponse> listarAlunosDasEntregas(List<Linha> linhas) {
        Map<Long, String> nomePorIdDoAluno = new LinkedHashMap<>();
        for (Linha linha : linhas) {
            Aluno aluno = linha.entrega().getEstagio().getMatricula().getAluno();
            // Se o aluno ainda não está no mapa, guardamos; se já está, ignoramos a repetição
            if (nomePorIdDoAluno.get(aluno.getId()) == null) {
                nomePorIdDoAluno.put(aluno.getId(), aluno.getNome());
            }
        }
        return paraOpcoesEmOrdemAlfabetica(nomePorIdDoAluno);
    }

    // Tarefas que aparecem nas entregas, cada uma uma vez só, para o filtro "Tarefa" da tela
    private static List<OpcaoFiltroResponse> listarTarefasDasEntregas(List<Linha> linhas) {
        Map<Long, String> nomePorIdDaTarefa = new LinkedHashMap<>();
        for (Linha linha : linhas) {
            Tarefa tarefa = linha.entrega().getTarefa();
            // Se a tarefa ainda não está no mapa, guardamos; se já está, ignoramos a repetição
            if (nomePorIdDaTarefa.get(tarefa.getId()) == null) {
                nomePorIdDaTarefa.put(tarefa.getId(), tarefa.getNome());
            }
        }
        return paraOpcoesEmOrdemAlfabetica(nomePorIdDaTarefa);
    }

    // Transforma o mapa (id e nome) na lista de opções do filtro, em ordem alfabética sem diferenciar maiúscula e minúscula
    private static List<OpcaoFiltroResponse> paraOpcoesEmOrdemAlfabetica(Map<Long, String> nomePorId) {
        List<OpcaoFiltroResponse> opcoes = new ArrayList<>();
        for (Map.Entry<Long, String> idENome : nomePorId.entrySet()) {
            opcoes.add(new OpcaoFiltroResponse(idENome.getKey(), idENome.getValue()));
        }
        opcoes.sort((primeira, segunda) -> String.CASE_INSENSITIVE_ORDER.compare(primeira.nome(), segunda.nome()));
        return opcoes;
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
