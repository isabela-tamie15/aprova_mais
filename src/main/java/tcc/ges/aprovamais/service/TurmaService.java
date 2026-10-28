package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.dto.TurmaRequest;
import tcc.ges.aprovamais.dto.TurmaResponse;
import tcc.ges.aprovamais.entity.Curso;
import tcc.ges.aprovamais.entity.Turma;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.TurmaRepository;

import java.util.List;
import java.util.Locale;

// Esse service é quem cuida do cadastro de turmas pelo coordenador (RF12, RN22)
@Service
@RequiredArgsConstructor
public class TurmaService {

    private static final Logger log = LoggerFactory.getLogger(TurmaService.class);

    private final TurmaRepository turmaRepository;
    private final CursoService cursoService;
    private final AuditoriaService auditoriaService;

    // Esse é o método que lista as turmas de um curso, desde que o curso seja do coordenador logado
    @Transactional(readOnly = true)
    public List<TurmaResponse> listar(Long cursoId, String emailCoordenador) {
        Curso curso = cursoService.buscarCursoDoCoordenador(cursoId, emailCoordenador);
        return turmaRepository.findByCursoIdOrderBySemestreDescIdentificadorAsc(curso.getId())
                .stream()
                .map(this::paraResponse)
                .toList();
    }

    /*
       Esse é o método que cadastra uma turma num curso do coordenador. Curso de
       outro coordenador cai no 404 do CursoService (RN21), e não pode haver duas
       turmas com o mesmo identificador no mesmo semestre do curso
    */
    @Transactional
    public TurmaResponse cadastrar(String emailCoordenador, TurmaRequest request, String ipOrigem) {
        Curso curso = cursoService.buscarCursoDoCoordenador(request.getCursoId(), emailCoordenador);
        String identificador = normalizarIdentificador(request.getIdentificador());

        if (turmaRepository.existsByCursoIdAndSemestreAndIdentificadorIgnoreCase(
                curso.getId(), request.getSemestre(), identificador)) {
            throw new IllegalStateException("Já existe a turma " + identificador + " neste semestre do curso");
        }

        Turma turma = new Turma();
        turma.setCurso(curso);
        preencher(turma, request, identificador);
        Turma salva = turmaRepository.save(turma);

        auditoriaService.registrar(curso.getCoordenador(), "TURMA_CADASTRADA",
                "Turma id=" + salva.getId() + " (" + identificador + " " + request.getSemestre()
                        + ") no curso id=" + curso.getId(), ipOrigem, true);
        log.info("[TURMA] Turma {} cadastrada no curso {} por {}", salva.getId(), curso.getId(), emailCoordenador);

        return paraResponse(salva);
    }

    /*
       Esse é o método que altera uma turma. A turma não muda de curso: isso
       levaria junto as matrículas e o orientador, então é recusado
    */
    @Transactional
    public TurmaResponse atualizar(Long turmaId, String emailCoordenador, TurmaRequest request, String ipOrigem) {
        Turma turma = turmaRepository.findByIdAndCursoCoordenadorEmail(turmaId, emailCoordenador)
                .orElseThrow(() -> new ResourceNotFoundException("Turma não encontrada"));

        if (!turma.getCurso().getId().equals(request.getCursoId())) {
            throw new IllegalStateException("Uma turma não pode mudar de curso");
        }

        String identificador = normalizarIdentificador(request.getIdentificador());
        if (turmaRepository.existsByCursoIdAndSemestreAndIdentificadorIgnoreCaseAndIdNot(
                turma.getCurso().getId(), request.getSemestre(), identificador, turmaId)) {
            throw new IllegalStateException("Já existe a turma " + identificador + " neste semestre do curso");
        }

        preencher(turma, request, identificador);
        Turma salva = turmaRepository.save(turma);

        auditoriaService.registrar(turma.getCurso().getCoordenador(), "TURMA_ALTERADA",
                "Turma id=" + turmaId + " alterada", ipOrigem, true);
        log.info("[TURMA] Turma {} alterada por {}", turmaId, emailCoordenador);

        return paraResponse(salva);
    }

    private void preencher(Turma turma, TurmaRequest request, String identificador) {
        turma.setSemestre(request.getSemestre());
        turma.setIdentificador(identificador);
        turma.setPeriodo(request.getPeriodo());
        turma.setModalidade(request.getModalidade());
    }

    // Grava o identificador em maiúsculas e sem espaços nas pontas, assim "8b " e "8B" são a mesma turma
    private String normalizarIdentificador(String identificador) {
        return identificador.trim().toUpperCase(Locale.ROOT);
    }

    private TurmaResponse paraResponse(Turma turma) {
        return TurmaResponse.builder()
                .id(turma.getId())
                .cursoId(turma.getCurso().getId())
                .nomeCurso(turma.getCurso().getNome())
                .semestre(turma.getSemestre())
                .identificador(turma.getIdentificador())
                .periodo(turma.getPeriodo().name())
                .modalidade(turma.getModalidade().name())
                .build();
    }
}
