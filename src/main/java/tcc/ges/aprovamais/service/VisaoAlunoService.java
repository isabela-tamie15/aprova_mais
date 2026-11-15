package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.dto.EstagioResponse;
import tcc.ges.aprovamais.dto.TarefaVisaoAlunoResponse;
import tcc.ges.aprovamais.dto.VisaoAlunoResponse;
import tcc.ges.aprovamais.entity.Entrega;
import tcc.ges.aprovamais.entity.Estagio;
import tcc.ges.aprovamais.entity.EntregaVersao;
import tcc.ges.aprovamais.entity.Tarefa;
import tcc.ges.aprovamais.entity.Trilha;
import tcc.ges.aprovamais.entity.enums.StatusEstagio;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.EntregaRepository;
import tcc.ges.aprovamais.repository.EntregaVersaoRepository;
import tcc.ges.aprovamais.repository.EstagioRepository;
import tcc.ges.aprovamais.repository.TarefaRepository;
import tcc.ges.aprovamais.repository.TrilhaRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/*
   Visão consolidada do aluno para o orientador.
   o progresso vem do EstagioService, e as tarefas, da trilha do tipo de estágio, com a entrega e as versões de cada uma
*/
@Service
@RequiredArgsConstructor
public class VisaoAlunoService {

    private static final String SEM_ENTREGA = "PENDENTE";

    private final EstagioRepository estagioRepository;
    private final EstagioService estagioService;
    private final TrilhaRepository trilhaRepository;
    private final TarefaRepository tarefaRepository;
    private final EntregaRepository entregaRepository;
    private final EntregaVersaoRepository entregaVersaoRepository;

    /*
       O estágio é buscado pelo id E pelo e-mail do orientador: estágio de outro orientador
       vira 404, sem revelar que existe
    */
    @Transactional(readOnly = true)
    public VisaoAlunoResponse montar(String emailOrientador, Long estagioId) {
        Estagio estagio = estagioRepository.findByIdAndOrientadorEmail(estagioId, emailOrientador)
                .orElseThrow(() -> new ResourceNotFoundException("Estágio não encontrado."));
        // A visão consolidada só existe para estágio ATIVO; nos outros status respondemos 404, como antes
        if (estagio.getStatus() != StatusEstagio.ATIVO) {
            throw new ResourceNotFoundException("Estágio não encontrado.");
        }

        // Progresso da F2 (horas, atividades e aviso de prazo) calculado só para este estágio
        EstagioResponse progresso = estagioService.montarProgresso(estagio);

        // Tarefas da trilha do tipo de estágio. Sem trilha, a lista fica vazia
        List<Tarefa> tarefas = new ArrayList<>();
        Optional<Trilha> trilhaDoTipo = trilhaRepository.findByTipoEstagioId(estagio.getTipoEstagio().getId());
        if (trilhaDoTipo.isPresent()) {
            tarefas = tarefaRepository.findByTrilhaIdOrderByOrdem(trilhaDoTipo.get().getId());
        }

        // Guarda a entrega de cada tarefa pelo id da tarefa. Cada tarefa tem no máximo uma entrega no estágio (uk_entrega_estagio_tarefa)
        Map<Long, Entrega> entregaPorTarefa = new HashMap<>();
        for (Entrega entrega : entregaRepository.findByEstagioId(estagio.getId())) {
            entregaPorTarefa.put(entrega.getTarefa().getId(), entrega);
        }

        // Uma linha por tarefa, com a situação da entrega do aluno (ou "sem entrega")
        List<TarefaVisaoAlunoResponse> linhasDasTarefas = new ArrayList<>();
        for (Tarefa tarefa : tarefas) {
            linhasDasTarefas.add(paraTarefa(tarefa, entregaPorTarefa.get(tarefa.getId())));
        }

        return VisaoAlunoResponse.builder()
                .progresso(progresso)
                .tarefas(linhasDasTarefas)
                .build();
    }

    private TarefaVisaoAlunoResponse paraTarefa(Tarefa tarefa, Entrega entrega) {
        TarefaVisaoAlunoResponse.TarefaVisaoAlunoResponseBuilder linha = TarefaVisaoAlunoResponse.builder()
                .tarefaId(tarefa.getId())
                .ordem(tarefa.getOrdem())
                .nomeTarefa(tarefa.getNome());

        if (entrega == null) {
            return linha.status(SEM_ENTREGA).numeroVersoes(0).build();
        }

        List<EntregaVersao> versoes = entregaVersaoRepository.findByEntregaIdOrderByNumeroAsc(entrega.getId());
        EntregaVersao comFeedback = ultimaComFeedback(versoes);

        return linha
                .entregaId(entrega.getId())
                .status(entrega.getStatus().name())
                .numeroVersoes(versoes.size())
                .ultimoFeedback(comFeedback != null ? comFeedback.getFeedback() : null)
                .versaoDoUltimoFeedback(comFeedback != null ? comFeedback.getNumero() : null)
                .ultimoFeedbackEm(comFeedback != null ? comFeedback.getAvaliadaEm() : null)
                .build();
    }

    // A aprovação pode vir sem feedback, então o último feedback é o da versão mais recente que tem um
    static EntregaVersao ultimaComFeedback(List<EntregaVersao> versoesEmOrdem) {
        for (int posicao = versoesEmOrdem.size() - 1; posicao >= 0; posicao--) {
            String feedback = versoesEmOrdem.get(posicao).getFeedback();
            if (feedback != null && !feedback.isBlank()) {
                return versoesEmOrdem.get(posicao);
            }
        }
        return null;
    }
}
