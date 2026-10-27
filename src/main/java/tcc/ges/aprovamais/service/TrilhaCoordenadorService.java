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
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.TarefaRepository;
import tcc.ges.aprovamais.repository.TipoEstagioRepository;
import tcc.ges.aprovamais.repository.TrilhaRepository;

import java.util.Optional;

/*
   Esse service é quem monta as trilhas e as tarefas pelo coordenador (RF15, RN32,
   RN33). A trilha que o aluno vê continua no TrilhaService; aqui fica só o lado
   da coordenação. Toda busca já filtra pelo coordenador logado, então trilha ou
   tarefa de curso de outro coordenador recebe 404 (RN21)
*/
@Service
@RequiredArgsConstructor
public class TrilhaCoordenadorService {

    private static final Logger log = LoggerFactory.getLogger(TrilhaCoordenadorService.class);

    private final TrilhaRepository trilhaRepository;
    private final TarefaRepository tarefaRepository;
    private final TipoEstagioRepository tipoEstagioRepository;
    private final AuditoriaService auditoriaService;

    // Esse é o método que busca a trilha de um tipo. Vazio quer dizer que o tipo ainda não tem trilha
    @Transactional(readOnly = true)
    public Optional<TrilhaDetalheResponse> buscarPorTipo(Long tipoEstagioId, String emailCoordenador) {
        TipoEstagio tipo = buscarTipoDoCoordenador(tipoEstagioId, emailCoordenador);
        return trilhaRepository.findByTipoEstagioId(tipo.getId()).map(this::paraResponse);
    }

    // Esse é o método que cria a trilha de um tipo de estágio. Cada tipo tem no máximo uma (RN32)
    @Transactional
    public TrilhaDetalheResponse criar(String emailCoordenador, TrilhaRequest request, String ipOrigem) {
        TipoEstagio tipo = buscarTipoDoCoordenador(request.getTipoEstagioId(), emailCoordenador);

        if (trilhaRepository.existsByTipoEstagioId(tipo.getId())) {
            throw new IllegalStateException("Este tipo de estágio já tem uma trilha");
        }

        Trilha trilha = new Trilha();
        trilha.setTipoEstagio(tipo);
        trilha.setNome(request.getNome().trim());
        trilha.setDescricao(textoOuNulo(request.getDescricao()));
        Trilha salva = trilhaRepository.save(trilha);

        registrar(tipo, "TRILHA_CRIADA", "Trilha id=" + salva.getId() + " criada para o tipo id=" + tipo.getId(), ipOrigem);
        log.info("[TRILHA] Trilha {} criada para o tipo {} por {}", salva.getId(), tipo.getId(), emailCoordenador);

        return paraResponse(salva);
    }

    // Esse é o método que altera o nome e a descrição da trilha. A trilha não troca de tipo
    @Transactional
    public TrilhaDetalheResponse atualizar(Long trilhaId, String emailCoordenador, TrilhaRequest request,
                                           String ipOrigem) {
        Trilha trilha = buscarTrilhaDoCoordenador(trilhaId, emailCoordenador);

        if (!trilha.getTipoEstagio().getId().equals(request.getTipoEstagioId())) {
            throw new IllegalStateException("Uma trilha não pode mudar de tipo de estágio");
        }

        trilha.setNome(request.getNome().trim());
        trilha.setDescricao(textoOuNulo(request.getDescricao()));
        Trilha salva = trilhaRepository.save(trilha);

        registrar(trilha.getTipoEstagio(), "TRILHA_ALTERADA", "Trilha id=" + trilhaId + " alterada", ipOrigem);
        return paraResponse(salva);
    }

    // Esse é o método que adiciona uma tarefa na trilha, numa posição que ainda esteja livre (RN33)
    @Transactional
    public TrilhaDetalheResponse adicionarTarefa(Long trilhaId, String emailCoordenador, TarefaRequest request,
                                                 String ipOrigem) {
        Trilha trilha = buscarTrilhaDoCoordenador(trilhaId, emailCoordenador);

        if (tarefaRepository.existsByTrilhaIdAndOrdem(trilhaId, request.getOrdem())) {
            throw new IllegalStateException("Já existe uma tarefa na posição " + request.getOrdem() + " desta trilha");
        }

        Tarefa tarefa = new Tarefa();
        tarefa.setTrilha(trilha);
        preencher(tarefa, request);
        Tarefa salva = tarefaRepository.save(tarefa);

        registrar(trilha.getTipoEstagio(), "TAREFA_ADICIONADA",
                "Tarefa id=" + salva.getId() + " adicionada na trilha id=" + trilhaId, ipOrigem);
        return paraResponse(trilha);
    }

    // Esse é o método que altera uma tarefa, inclusive a posição dela na trilha
    @Transactional
    public TrilhaDetalheResponse atualizarTarefa(Long tarefaId, String emailCoordenador, TarefaRequest request,
                                                 String ipOrigem) {
        Tarefa tarefa = buscarTarefaDoCoordenador(tarefaId, emailCoordenador);
        Trilha trilha = tarefa.getTrilha();

        if (tarefaRepository.existsByTrilhaIdAndOrdemAndIdNot(trilha.getId(), request.getOrdem(), tarefaId)) {
            throw new IllegalStateException("Já existe uma tarefa na posição " + request.getOrdem() + " desta trilha");
        }

        preencher(tarefa, request);
        tarefaRepository.save(tarefa);

        registrar(trilha.getTipoEstagio(), "TAREFA_ALTERADA", "Tarefa id=" + tarefaId + " alterada", ipOrigem);
        return paraResponse(trilha);
    }

    /*
       Esse é o método que remove uma tarefa da trilha. Por enquanto pode remover
       sempre, porque as entregas ainda não existem. Quando o envio de entregas
       for implementado, a remoção precisa ser bloqueada para tarefa que já tem
       entrega, senão o histórico do aluno perde a referência
    */
    @Transactional
    public void removerTarefa(Long tarefaId, String emailCoordenador, String ipOrigem) {
        Tarefa tarefa = buscarTarefaDoCoordenador(tarefaId, emailCoordenador);
        TipoEstagio tipo = tarefa.getTrilha().getTipoEstagio();

        tarefaRepository.delete(tarefa);

        registrar(tipo, "TAREFA_REMOVIDA",
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
    private void registrar(TipoEstagio tipo, String acao, String detalhes, String ipOrigem) {
        Coordenador coordenador = tipo.getCurso().getCoordenador();
        auditoriaService.registrar(coordenador, acao, detalhes, ipOrigem, true);
    }

    // As tarefas vêm do repositório, já na ordem em que o aluno vê
    private TrilhaDetalheResponse paraResponse(Trilha trilha) {
        return TrilhaDetalheResponse.builder()
                .id(trilha.getId())
                .tipoEstagioId(trilha.getTipoEstagio().getId())
                .nomeTipoEstagio(trilha.getTipoEstagio().getNome())
                .nome(trilha.getNome())
                .descricao(trilha.getDescricao())
                .tarefas(tarefaRepository.findByTrilhaIdOrderByOrdem(trilha.getId())
                        .stream()
                        .map(t -> TarefaResponse.builder()
                                .id(t.getId())
                                .nome(t.getNome())
                                .descricao(t.getDescricao())
                                .ordem(t.getOrdem())
                                .linkApoio(t.getLinkApoio())
                                .build())
                        .toList())
                .build();
    }
}
