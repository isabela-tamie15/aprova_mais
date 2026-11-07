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
import tcc.ges.aprovamais.entity.enums.StatusEstagio;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.EntregaRepository;
import tcc.ges.aprovamais.repository.EntregaVersaoRepository;
import tcc.ges.aprovamais.repository.EstagioRepository;
import tcc.ges.aprovamais.repository.TarefaRepository;
import tcc.ges.aprovamais.repository.TrilhaRepository;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
                .filter(e -> e.getStatus() == StatusEstagio.ATIVO)
                .orElseThrow(() -> new ResourceNotFoundException("Estágio não encontrado."));

        EstagioResponse progresso = estagioService.listarAtivosDoOrientador(emailOrientador).stream()
                .filter(e -> e.getId().equals(estagio.getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Estágio não encontrado."));

        List<Tarefa> tarefas = trilhaRepository.findByTipoEstagioId(estagio.getTipoEstagio().getId())
                .map(trilha -> tarefaRepository.findByTrilhaIdOrderByOrdem(trilha.getId()))
                .orElse(List.of());

        // Cada tarefa tem no máximo uma entrega no estágio (uk_entrega_estagio_tarefa)
        Map<Long, Entrega> entregaPorTarefa = entregaRepository.findByEstagioId(estagio.getId()).stream()
                .collect(Collectors.toMap(e -> e.getTarefa().getId(), Function.identity()));

        return VisaoAlunoResponse.builder()
                .progresso(progresso)
                .tarefas(tarefas.stream().map(t -> paraTarefa(t, entregaPorTarefa.get(t.getId()))).toList())
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
        for (int i = versoesEmOrdem.size() - 1; i >= 0; i--) {
            String feedback = versoesEmOrdem.get(i).getFeedback();
            if (feedback != null && !feedback.isBlank()) {
                return versoesEmOrdem.get(i);
            }
        }
        return null;
    }
}
