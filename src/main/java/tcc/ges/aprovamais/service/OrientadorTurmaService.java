package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.dto.OpcaoFiltroResponse;
import tcc.ges.aprovamais.dto.TurmaResponse;
import tcc.ges.aprovamais.entity.Estagio;
import tcc.ges.aprovamais.entity.Orientador;
import tcc.ges.aprovamais.entity.OrientadorTurma;
import tcc.ges.aprovamais.entity.Turma;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.EstagioRepository;
import tcc.ges.aprovamais.repository.OrientadorRepository;
import tcc.ges.aprovamais.repository.OrientadorTurmaRepository;
import tcc.ges.aprovamais.repository.TurmaRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/*
   Atribuição do orientador de cada turma pelo coordenador. A turma tem um orientador só, e o
   mesmo orientador pode ficar com várias turmas
*/
@Service
@RequiredArgsConstructor
public class OrientadorTurmaService {

    private static final Logger log = LoggerFactory.getLogger(OrientadorTurmaService.class);

    private final TurmaRepository turmaRepository;
    private final OrientadorRepository orientadorRepository;
    private final OrientadorTurmaRepository orientadorTurmaRepository;
    private final EstagioRepository estagioRepository;
    private final TurmaService turmaService;
    private final AuditoriaService auditoriaService;

    // Orientadores com conta ativa, para a lista de seleção da tela de turmas
    @Transactional(readOnly = true)
    public List<OpcaoFiltroResponse> listarOrientadoresDisponiveis() {
        List<OpcaoFiltroResponse> opcoes = new ArrayList<>();
        for (Orientador orientador : orientadorRepository.findByAtivoTrueOrderByNomeAsc()) {
            opcoes.add(new OpcaoFiltroResponse(orientador.getId(), orientador.getNome()));
        }
        return opcoes;
    }

    /*
       Atribui ou troca o orientador da turma. Os estágios em aberto passam para o novo orientador,
       para nenhum aluno ficar sem quem valide e corrija. Os concluídos e encerrados ficam com quem
       acompanhou, e o histórico guarda o orientador de cada momento
    */
    @Transactional
    public TurmaResponse atribuirOrientador(Long turmaId, Long orientadorId, String emailCoordenador, String ipOrigem) {
        // Turma de curso de outro coordenador volta 404
        Turma turma = turmaRepository.findByIdAndCursoCoordenadorEmail(turmaId, emailCoordenador)
                .orElseThrow(() -> new ResourceNotFoundException("Turma não encontrada"));

        // Só orientador com conta ativa recebe turma
        Orientador orientador = orientadorRepository.findByIdAndAtivoTrue(orientadorId)
                .orElseThrow(() -> new ResourceNotFoundException("Orientador não encontrado"));

        // Altera o vínculo que já existe ou cria um novo
        Optional<OrientadorTurma> vinculoExistente = orientadorTurmaRepository.findByTurmaId(turmaId);
        OrientadorTurma vinculo;
        Long idDoOrientadorAnterior = null;
        if (vinculoExistente.isPresent()) {
            vinculo = vinculoExistente.get();
            idDoOrientadorAnterior = vinculo.getOrientador().getId();
        } else {
            vinculo = new OrientadorTurma();
            vinculo.setTurma(turma);
        }
        vinculo.setOrientador(orientador);
        orientadorTurmaRepository.save(vinculo);

        // Passa os estágios em aberto da turma para o novo orientador, inclusive os que estavam sem orientador
        int estagiosTransferidos = 0;
        List<Estagio> estagiosEmAberto = estagioRepository.findByMatriculaTurmaIdAndStatusIn(
                turmaId, EstagioService.STATUS_EM_ABERTO);
        for (Estagio estagio : estagiosEmAberto) {
            boolean jaEraDesseOrientador = estagio.getOrientador() != null
                    && estagio.getOrientador().getId().equals(orientador.getId());
            if (!jaEraDesseOrientador) {
                estagio.setOrientador(orientador);
                estagioRepository.save(estagio);
                estagiosTransferidos++;
            }
        }

        String detalhe = "Turma id=" + turmaId + ": orientador id=" + orientador.getId()
                + (idDoOrientadorAnterior != null ? " (antes id=" + idDoOrientadorAnterior + ")" : "")
                + ", " + estagiosTransferidos + " estágio(s) em aberto transferido(s)";
        auditoriaService.registrar(turma.getCurso().getCoordenador(), "ORIENTADOR_ATRIBUIDO", detalhe, ipOrigem, true);
        log.info("[TURMA] {} por {}", detalhe, emailCoordenador);

        return turmaService.paraResponse(turma, orientador);
    }
}
