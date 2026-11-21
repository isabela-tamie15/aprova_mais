package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.dto.TarefaRequest;
import tcc.ges.aprovamais.dto.TarefaResponse;
import tcc.ges.aprovamais.dto.TrilhaDetalheResponse;
import tcc.ges.aprovamais.dto.TrilhaRequest;
import tcc.ges.aprovamais.entity.Coordenador;
import tcc.ges.aprovamais.entity.Tarefa;
import tcc.ges.aprovamais.entity.TipoEstagio;
import tcc.ges.aprovamais.entity.Trilha;
import tcc.ges.aprovamais.exception.RegraNegocioException;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.CriterioChecklistRepository;
import tcc.ges.aprovamais.repository.EntregaRepository;
import tcc.ges.aprovamais.repository.TarefaRepository;
import tcc.ges.aprovamais.repository.TipoEstagioRepository;
import tcc.ges.aprovamais.repository.TrilhaRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/*
   Montagem das trilhas e tarefas pelo coordenador. A trilha vista pelo aluno fica no TrilhaService.
   Toda busca filtra pelo coordenador logado, e a trilha de outro coordenador vai retornar um 404
*/
@Service
@RequiredArgsConstructor
public class TrilhaCoordenadorService {

    private static final Logger log = LoggerFactory.getLogger(TrilhaCoordenadorService.class);

    private final TrilhaRepository trilhaRepository;
    private final TarefaRepository tarefaRepository;
    private final TipoEstagioRepository tipoEstagioRepository;
    private final AuditoriaService auditoriaService;
    private final EntregaRepository entregaRepository;
    private final CriterioChecklistRepository criterioChecklistRepository;

    // Busca a trilha de um tipo. Vazio quer dizer que o tipo ainda não tem trilha
    @Transactional(readOnly = true)
    public Optional<TrilhaDetalheResponse> buscarPorTipo(Long tipoEstagioId, String emailCoordenador) {
        TipoEstagio tipoEstagio = buscarTipoDoCoordenador(tipoEstagioId, emailCoordenador);
        Optional<Trilha> trilhaDoTipo = trilhaRepository.findByTipoEstagioId(tipoEstagio.getId());
        if (trilhaDoTipo.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(paraResponse(trilhaDoTipo.get()));
    }

    // Cria a trilha de um tipo de estágio. Cada tipo tem no máximo uma
    @Transactional
    public TrilhaDetalheResponse criar(String emailCoordenador, TrilhaRequest request, String ipOrigem) {
        TipoEstagio tipoEstagio = buscarTipoDoCoordenador(request.getTipoEstagioId(), emailCoordenador);

        if (trilhaRepository.existsByTipoEstagioId(tipoEstagio.getId())) {
            throw new RegraNegocioException("Este tipo de estágio já tem uma trilha");
        }

        Trilha trilha = new Trilha();
        trilha.setTipoEstagio(tipoEstagio);
        trilha.setNome(request.getNome().trim());
        trilha.setDescricao(textoOuNulo(request.getDescricao()));
        Trilha salva = trilhaRepository.save(trilha);

        registrar(tipoEstagio, "TRILHA_CRIADA", "Trilha id=" + salva.getId() + " criada para o tipo id=" + tipoEstagio.getId(), ipOrigem);
        log.info("[TRILHA] Trilha {} criada para o tipo {} por {}", salva.getId(), tipoEstagio.getId(), emailCoordenador);

        return paraResponse(salva);
    }

    // Altera o nome e a descrição da trilha. A trilha não troca de tipo
    @Transactional
    public TrilhaDetalheResponse atualizar(Long trilhaId, String emailCoordenador, TrilhaRequest request,
                                           String ipOrigem) {
        Trilha trilha = buscarTrilhaDoCoordenador(trilhaId, emailCoordenador);

        if (!trilha.getTipoEstagio().getId().equals(request.getTipoEstagioId())) {
            throw new RegraNegocioException("Uma trilha não pode mudar de tipo de estágio");
        }

        trilha.setNome(request.getNome().trim());
        trilha.setDescricao(textoOuNulo(request.getDescricao()));
        Trilha salva = trilhaRepository.save(trilha);

        registrar(trilha.getTipoEstagio(), "TRILHA_ALTERADA", "Trilha id=" + trilhaId + " alterada", ipOrigem);
        return paraResponse(salva);
    }

    // Adiciona uma tarefa na trilha, numa posição ainda livre
    @Transactional
    public TrilhaDetalheResponse adicionarTarefa(Long trilhaId, String emailCoordenador, TarefaRequest request,
                                                 String ipOrigem) {
        Trilha trilha = buscarTrilhaDoCoordenador(trilhaId, emailCoordenador);

        if (tarefaRepository.existsByTrilhaIdAndOrdem(trilhaId, request.getOrdem())) {
            throw new RegraNegocioException("Já existe uma tarefa na posição " + request.getOrdem() + " desta trilha");
        }

        Tarefa tarefa = new Tarefa();
        tarefa.setTrilha(trilha);
        preencher(tarefa, request);
        Tarefa salva = tarefaRepository.save(tarefa);

        registrar(trilha.getTipoEstagio(), "TAREFA_ADICIONADA",
                "Tarefa id=" + salva.getId() + " adicionada na trilha id=" + trilhaId, ipOrigem);
        return paraResponse(trilha);
    }

    // Altera uma tarefa, inclusive a posição dela na trilha
    @Transactional
    public TrilhaDetalheResponse atualizarTarefa(Long tarefaId, String emailCoordenador, TarefaRequest request,
                                                 String ipOrigem) {
        Tarefa tarefa = buscarTarefaDoCoordenador(tarefaId, emailCoordenador);
        Trilha trilha = tarefa.getTrilha();

        if (tarefaRepository.existsByTrilhaIdAndOrdemAndIdNot(trilha.getId(), request.getOrdem(), tarefaId)) {
            throw new RegraNegocioException("Já existe uma tarefa na posição " + request.getOrdem() + " desta trilha");
        }

        preencher(tarefa, request);
        tarefaRepository.save(tarefa);

        registrar(trilha.getTipoEstagio(), "TAREFA_ALTERADA", "Tarefa id=" + tarefaId + " alterada", ipOrigem);
        return paraResponse(trilha);
    }

    // Remove uma tarefa da trilha. A tarefa que já tem entrega de aluno não pode sair, para o histórico do aluno não perder a referência
    @Transactional
    public void removerTarefa(Long tarefaId, String emailCoordenador, String ipOrigem) {
        Tarefa tarefa = buscarTarefaDoCoordenador(tarefaId, emailCoordenador);
        TipoEstagio tipoEstagio = tarefa.getTrilha().getTipoEstagio();

        if (entregaRepository.existsByTarefaId(tarefa.getId())) {
            throw new RegraNegocioException("Esta tarefa já tem entregas de alunos e não pode ser removida.");
        }
        // Sem entregas, a tarefa não tem correções, então os critérios do checklist dela saem junto
        criterioChecklistRepository.deleteAll(criterioChecklistRepository.findByTarefaId(tarefa.getId()));
        tarefaRepository.delete(tarefa);

        registrar(tipoEstagio, "TAREFA_REMOVIDA",
                "Tarefa id=" + tarefaId + " removida da trilha id=" + tarefa.getTrilha().getId(), ipOrigem);
        log.info("[TRILHA] Tarefa {} removida por {}", tarefaId, emailCoordenador);
    }

    // métodos auxiliares

    private TipoEstagio buscarTipoDoCoordenador(Long tipoEstagioId, String emailCoordenador) {
        return tipoEstagioRepository.findByIdAndCursoCoordenadorEmail(tipoEstagioId, emailCoordenador)
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de estágio não encontrado"));
    }

    private Trilha buscarTrilhaDoCoordenador(Long trilhaId, String emailCoordenador) {
        return trilhaRepository.findByIdAndTipoEstagioCursoCoordenadorEmail(trilhaId, emailCoordenador)
                .orElseThrow(() -> new ResourceNotFoundException("Trilha não encontrada"));
    }

    private Tarefa buscarTarefaDoCoordenador(Long tarefaId, String emailCoordenador) {
        return tarefaRepository.findByIdAndTrilhaTipoEstagioCursoCoordenadorEmail(tarefaId, emailCoordenador)
                .orElseThrow(() -> new ResourceNotFoundException("Tarefa não encontrada"));
    }

    private void preencher(Tarefa tarefa, TarefaRequest request) {
        tarefa.setNome(request.getNome().trim());
        tarefa.setDescricao(textoOuNulo(request.getDescricao()));
        tarefa.setOrdem(request.getOrdem());
        tarefa.setLinkApoio(textoOuNulo(request.getLinkApoio()));
    }

    // Campo opcional em branco é gravado como nulo, assim a tela não mostra um texto vazio
    private String textoOuNulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    // Quem responde na auditoria é o coordenador do curso do tipo de estágio
    private void registrar(TipoEstagio tipoEstagio, String acao, String detalhes, String ipOrigem) {
        Coordenador coordenador = tipoEstagio.getCurso().getCoordenador();
        auditoriaService.registrar(coordenador, acao, detalhes, ipOrigem, true);
    }

    // As tarefas vêm do repositório, já na ordem em que o aluno vê
    private TrilhaDetalheResponse paraResponse(Trilha trilha) {
        List<Tarefa> tarefasDaTrilha = tarefaRepository.findByTrilhaIdOrderByOrdem(trilha.getId());

        // Transforma cada tarefa do banco na resposta que vai para a tela de montagem da trilha
        List<TarefaResponse> tarefas = new ArrayList<>();
        for (Tarefa tarefa : tarefasDaTrilha) {
            tarefas.add(TarefaResponse.builder()
                    .id(tarefa.getId())
                    .nome(tarefa.getNome())
                    .descricao(tarefa.getDescricao())
                    .ordem(tarefa.getOrdem())
                    .linkApoio(tarefa.getLinkApoio())
                    .build());
        }

        return TrilhaDetalheResponse.builder()
                .id(trilha.getId())
                .tipoEstagioId(trilha.getTipoEstagio().getId())
                .nomeTipoEstagio(trilha.getTipoEstagio().getNome())
                .nome(trilha.getNome())
                .descricao(trilha.getDescricao())
                .tarefas(tarefas)
                .build();
    }
}
