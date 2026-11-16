package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.dto.CriterioChecklistRequest;
import tcc.ges.aprovamais.dto.CriterioChecklistResponse;
import tcc.ges.aprovamais.dto.RespostaCriterioRequest;
import tcc.ges.aprovamais.dto.RespostaCriterioResponse;
import tcc.ges.aprovamais.entity.Coordenador;
import tcc.ges.aprovamais.entity.CriterioChecklist;
import tcc.ges.aprovamais.entity.EntregaVersao;
import tcc.ges.aprovamais.entity.RespostaCriterioChecklist;
import tcc.ges.aprovamais.entity.Tarefa;
import tcc.ges.aprovamais.entity.enums.ResultadoCriterio;
import tcc.ges.aprovamais.exception.DadoInvalidoException;
import tcc.ges.aprovamais.exception.RegraNegocioException;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.CriterioChecklistRepository;
import tcc.ges.aprovamais.repository.RespostaCriterioChecklistRepository;
import tcc.ges.aprovamais.repository.TarefaRepository;

import java.util.ArrayList;
import java.util.List;

/*
   Checklist de critérios das entregas (F6). O coordenador cadastra os critérios de cada tarefa,
   e o orientador marca cada um como suficiente ou insuficiente ao corrigir. Toda busca do
   coordenador filtra pelos cursos dele, e a tarefa de outro coordenador recebe 404
*/
@Service
@RequiredArgsConstructor
public class ChecklistService {

    private static final Logger log = LoggerFactory.getLogger(ChecklistService.class);

    private final CriterioChecklistRepository criterioChecklistRepository;
    private final RespostaCriterioChecklistRepository respostaCriterioChecklistRepository;
    private final TarefaRepository tarefaRepository;
    private final AuditoriaService auditoriaService;

    // ---------- critérios, cadastrados pelo coordenador ----------

    // Critérios ativos de uma tarefa dos cursos do coordenador
    @Transactional(readOnly = true)
    public List<CriterioChecklistResponse> listarDaTarefa(Long tarefaId, String emailCoordenador) {
        Tarefa tarefa = buscarTarefaDoCoordenador(tarefaId, emailCoordenador);
        return listarCriteriosAtivos(tarefa.getId());
    }

    // Adiciona um critério na tarefa, numa posição ainda livre
    @Transactional
    public List<CriterioChecklistResponse> adicionar(Long tarefaId, String emailCoordenador,
                                                     CriterioChecklistRequest request, String ipOrigem) {
        Tarefa tarefa = buscarTarefaDoCoordenador(tarefaId, emailCoordenador);

        if (criterioChecklistRepository.existsByTarefaIdAndOrdemAndAtivoTrue(tarefa.getId(), request.getOrdem())) {
            throw new RegraNegocioException("Já existe um critério na posição " + request.getOrdem() + " desta tarefa");
        }

        CriterioChecklist criterio = new CriterioChecklist();
        criterio.setTarefa(tarefa);
        criterio.setDescricao(request.getDescricao().trim());
        criterio.setOrdem(request.getOrdem());
        criterio.setAtivo(true);
        CriterioChecklist salvo = criterioChecklistRepository.save(criterio);

        registrar(tarefa, "CRITERIO_ADICIONADO",
                "Critério id=" + salvo.getId() + " adicionado na tarefa id=" + tarefa.getId(), ipOrigem);
        log.info("[CHECKLIST] Critério {} adicionado na tarefa {} por {}", salvo.getId(), tarefa.getId(), emailCoordenador);

        return listarCriteriosAtivos(tarefa.getId());
    }

    /*
       Altera a descrição e a posição de um critério. As correções antigas não mudam, porque cada
       resposta guardou a sua própria cópia da descrição
    */
    @Transactional
    public List<CriterioChecklistResponse> alterar(Long criterioId, String emailCoordenador,
                                                   CriterioChecklistRequest request, String ipOrigem) {
        CriterioChecklist criterio = buscarCriterioDoCoordenador(criterioId, emailCoordenador);
        Tarefa tarefa = criterio.getTarefa();

        if (criterioChecklistRepository.existsByTarefaIdAndOrdemAndAtivoTrueAndIdNot(
                tarefa.getId(), request.getOrdem(), criterioId)) {
            throw new RegraNegocioException("Já existe um critério na posição " + request.getOrdem() + " desta tarefa");
        }

        criterio.setDescricao(request.getDescricao().trim());
        criterio.setOrdem(request.getOrdem());
        criterioChecklistRepository.save(criterio);

        registrar(tarefa, "CRITERIO_ALTERADO", "Critério id=" + criterioId + " alterado", ipOrigem);
        return listarCriteriosAtivos(tarefa.getId());
    }

    /*
       Remove um critério da tarefa. O critério que nunca foi usado é apagado. O que já foi usado em
       uma correção só é desativado: as respostas gravadas apontam para ele e não podem ficar sem dono
    */
    @Transactional
    public void remover(Long criterioId, String emailCoordenador, String ipOrigem) {
        CriterioChecklist criterio = buscarCriterioDoCoordenador(criterioId, emailCoordenador);
        Tarefa tarefa = criterio.getTarefa();

        if (respostaCriterioChecklistRepository.existsByCriterioId(criterio.getId())) {
            criterio.setAtivo(false);
            criterioChecklistRepository.save(criterio);
            registrar(tarefa, "CRITERIO_DESATIVADO", "Critério id=" + criterioId + " desativado", ipOrigem);
            log.info("[CHECKLIST] Critério {} já usado, desativado por {}", criterioId, emailCoordenador);
        } else {
            criterioChecklistRepository.delete(criterio);
            registrar(tarefa, "CRITERIO_REMOVIDO", "Critério id=" + criterioId + " removido", ipOrigem);
            log.info("[CHECKLIST] Critério {} removido por {}", criterioId, emailCoordenador);
        }
    }

    // Critérios ativos da tarefa, na ordem. Também é usado na tela de correção do orientador
    @Transactional(readOnly = true)
    public List<CriterioChecklistResponse> listarCriteriosAtivos(Long tarefaId) {
        List<CriterioChecklistResponse> criterios = new ArrayList<>();
        for (CriterioChecklist criterio : criterioChecklistRepository.findByTarefaIdAndAtivoTrueOrderByOrdem(tarefaId)) {
            criterios.add(CriterioChecklistResponse.builder()
                    .id(criterio.getId())
                    .descricao(criterio.getDescricao())
                    .ordem(criterio.getOrdem())
                    .build());
        }
        return criterios;
    }

    // ---------- respostas, marcadas pelo orientador na correção ----------

    /*
       Confere o checklist enviado pelo orientador e grava as respostas na versão corrigida.
       Regras: todo critério ativo da tarefa precisa ser marcado, e com algum critério insuficiente
       a entrega não pode ser aprovada, só devolvida. Tarefa sem critérios não grava nada
    */
    @Transactional
    public void gravarRespostas(EntregaVersao versao, Tarefa tarefa, List<RespostaCriterioRequest> respostasRecebidas,
                                boolean aprovar) {
        List<CriterioChecklist> criteriosAtivos = criterioChecklistRepository.findByTarefaIdAndAtivoTrueOrderByOrdem(tarefa.getId());

        // Tarefa sem critérios é corrigida como antes, só com a decisão e o feedback
        if (criteriosAtivos.isEmpty()) {
            return;
        }

        // Se a tela não mandou nenhuma resposta, tratamos como uma lista vazia
        List<RespostaCriterioRequest> respostas = new ArrayList<>();
        if (respostasRecebidas != null) {
            respostas.addAll(respostasRecebidas);
        }

        // Cada critério ativo precisa de exatamente uma resposta
        if (respostas.size() != criteriosAtivos.size()) {
            throw new DadoInvalidoException("Marque suficiente ou insuficiente em todos os critérios.");
        }

        boolean algumCriterioInsuficiente = false;
        List<RespostaCriterioChecklist> respostasParaGravar = new ArrayList<>();

        // Percorremos os critérios na ordem do checklist, procurando a resposta de cada um
        for (CriterioChecklist criterio : criteriosAtivos) {
            RespostaCriterioRequest respostaDoCriterio = buscarRespostaDoCriterio(respostas, criterio.getId());
            if (respostaDoCriterio == null || respostaDoCriterio.getResultado() == null) {
                throw new DadoInvalidoException("Marque suficiente ou insuficiente em todos os critérios.");
            }
            if (respostaDoCriterio.getResultado() == ResultadoCriterio.INSUFICIENTE) {
                algumCriterioInsuficiente = true;
            }

            RespostaCriterioChecklist resposta = new RespostaCriterioChecklist();
            resposta.setVersao(versao);
            resposta.setCriterio(criterio);
            resposta.setDescricaoCriterio(criterio.getDescricao());
            resposta.setResultado(respostaDoCriterio.getResultado());
            resposta.setObservacao(textoOuNulo(respostaDoCriterio.getObservacao()));
            respostasParaGravar.add(resposta);
        }

        // A entrega só pode ser aprovada se todos os critérios estiverem suficientes
        if (aprovar && algumCriterioInsuficiente) {
            throw new RegraNegocioException("Há critério insuficiente. Devolva a entrega para o aluno corrigir.");
        }

        respostaCriterioChecklistRepository.saveAll(respostasParaGravar);
    }

    // Respostas de uma versão corrigida, na ordem do checklist, para o histórico do aluno e do orientador
    @Transactional(readOnly = true)
    public List<RespostaCriterioResponse> listarRespostasDaVersao(Long versaoId) {
        List<RespostaCriterioResponse> respostas = new ArrayList<>();
        for (RespostaCriterioChecklist resposta : respostaCriterioChecklistRepository.findByVersaoIdOrderByIdAsc(versaoId)) {
            respostas.add(RespostaCriterioResponse.builder()
                    .descricaoCriterio(resposta.getDescricaoCriterio())
                    .resultado(resposta.getResultado().name())
                    .observacao(resposta.getObservacao())
                    .build());
        }
        return respostas;
    }

    // métodos auxiliares

    private Tarefa buscarTarefaDoCoordenador(Long tarefaId, String emailCoordenador) {
        return tarefaRepository.findByIdAndTrilhaTipoEstagioCursoCoordenadorEmail(tarefaId, emailCoordenador)
                .orElseThrow(() -> new ResourceNotFoundException("Tarefa não encontrada"));
    }

    private CriterioChecklist buscarCriterioDoCoordenador(Long criterioId, String emailCoordenador) {
        return criterioChecklistRepository
                .findByIdAndAtivoTrueAndTarefaTrilhaTipoEstagioCursoCoordenadorEmail(criterioId, emailCoordenador)
                .orElseThrow(() -> new ResourceNotFoundException("Critério não encontrado"));
    }

    // Procura, na lista enviada pela tela, a resposta de um critério. Devolve null quando ele ficou sem resposta
    private RespostaCriterioRequest buscarRespostaDoCriterio(List<RespostaCriterioRequest> respostas, Long criterioId) {
        for (RespostaCriterioRequest resposta : respostas) {
            if (criterioId.equals(resposta.getCriterioId())) {
                return resposta;
            }
        }
        return null;
    }

    // Observação em branco é gravada como nula, assim a tela não mostra um texto vazio
    private String textoOuNulo(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return texto.trim();
    }

    // Quem responde na auditoria é o coordenador do curso da tarefa
    private void registrar(Tarefa tarefa, String acao, String detalhes, String ipOrigem) {
        Coordenador coordenador = tarefa.getTrilha().getTipoEstagio().getCurso().getCoordenador();
        auditoriaService.registrar(coordenador, acao, detalhes, ipOrigem, true);
    }
}
