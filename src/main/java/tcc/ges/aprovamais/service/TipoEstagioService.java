package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.dto.TipoEstagioRequest;
import tcc.ges.aprovamais.dto.TipoEstagioResponse;
import tcc.ges.aprovamais.entity.Curso;
import tcc.ges.aprovamais.entity.TipoEstagio;
import tcc.ges.aprovamais.exception.RegraNegocioException;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.TipoEstagioRepository;

import java.util.ArrayList;
import java.util.List;

// Cadastro dos tipos de estágio pelo coordenador. A lista que o aluno vê no cadastro do estágio fica no EstagioService
@Service
@RequiredArgsConstructor
public class TipoEstagioService {

    private static final Logger log = LoggerFactory.getLogger(TipoEstagioService.class);

    private final TipoEstagioRepository tipoEstagioRepository;
    private final CursoService cursoService;
    private final AuditoriaService auditoriaService;

    // Lista os tipos de um curso do coordenador logado
    @Transactional(readOnly = true)
    public List<TipoEstagioResponse> listar(Long cursoId, String emailCoordenador) {
        Curso curso = cursoService.buscarCursoDoCoordenador(cursoId, emailCoordenador);
        List<TipoEstagio> tiposDoCurso = tipoEstagioRepository.findByCursoIdOrderByNomeAsc(curso.getId());

        List<TipoEstagioResponse> respostas = new ArrayList<>();
        for (TipoEstagio tipoEstagio : tiposDoCurso) {
            respostas.add(paraResponse(tipoEstagio));
        }
        return respostas;
    }

    // Cadastra um tipo de estágio num curso do coordenador
    @Transactional
    public TipoEstagioResponse cadastrar(String emailCoordenador, TipoEstagioRequest request, String ipOrigem) {
        Curso curso = cursoService.buscarCursoDoCoordenador(request.getCursoId(), emailCoordenador);
        String nomeDoTipoEstagio = request.getNome().trim();

        if (tipoEstagioRepository.existsByCursoIdAndNomeIgnoreCase(curso.getId(), nomeDoTipoEstagio)) {
            throw new RegraNegocioException("Já existe o tipo de estágio " + nomeDoTipoEstagio + " neste curso");
        }

        TipoEstagio tipoEstagio = new TipoEstagio();
        tipoEstagio.setCurso(curso);
        preencher(tipoEstagio, request, nomeDoTipoEstagio);
        TipoEstagio salvo = tipoEstagioRepository.save(tipoEstagio);

        auditoriaService.registrar(curso.getCoordenador(), "TIPO_ESTAGIO_CADASTRADO",
                "Tipo de estágio id=" + salvo.getId() + " (" + nomeDoTipoEstagio + ") no curso id=" + curso.getId(),
                ipOrigem, true);
        log.info("[TIPO ESTÁGIO] Tipo {} cadastrado no curso {} por {}", salvo.getId(), curso.getId(), emailCoordenador);

        return paraResponse(salvo);
    }

    /*
       Altera um tipo de estágio. Os estágios já cadastrados guardam uma cópia da carga e da jornada,
       então só os novos usam os valores alterados
    */
    @Transactional
    public TipoEstagioResponse atualizar(Long tipoId, String emailCoordenador, TipoEstagioRequest request,
                                         String ipOrigem) {
        TipoEstagio tipoEstagio = tipoEstagioRepository.findByIdAndCursoCoordenadorEmail(tipoId, emailCoordenador)
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de estágio não encontrado"));

        if (!tipoEstagio.getCurso().getId().equals(request.getCursoId())) {
            throw new RegraNegocioException("Um tipo de estágio não pode mudar de curso");
        }

        String nomeDoTipoEstagio = request.getNome().trim();
        if (tipoEstagioRepository.existsByCursoIdAndNomeIgnoreCaseAndIdNot(tipoEstagio.getCurso().getId(), nomeDoTipoEstagio, tipoId)) {
            throw new RegraNegocioException("Já existe o tipo de estágio " + nomeDoTipoEstagio + " neste curso");
        }

        preencher(tipoEstagio, request, nomeDoTipoEstagio);
        TipoEstagio salvo = tipoEstagioRepository.save(tipoEstagio);

        auditoriaService.registrar(tipoEstagio.getCurso().getCoordenador(), "TIPO_ESTAGIO_ALTERADO",
                "Tipo de estágio id=" + tipoId + " alterado (carga " + request.getCargaHorariaNecessaria()
                        + " h, jornada " + request.getJornadaSemanal() + " h)", ipOrigem, true);
        log.info("[TIPO ESTÁGIO] Tipo {} alterado por {}", tipoId, emailCoordenador);

        return paraResponse(salvo);
    }

    private void preencher(TipoEstagio tipoEstagio, TipoEstagioRequest request, String nomeDoTipoEstagio) {
        tipoEstagio.setNome(nomeDoTipoEstagio);
        tipoEstagio.setDescricao(request.getDescricao() == null || request.getDescricao().isBlank()
                ? null
                : request.getDescricao().trim());
        tipoEstagio.setCargaHorariaNecessaria(request.getCargaHorariaNecessaria());
        tipoEstagio.setJornadaSemanal(request.getJornadaSemanal());
    }

    private TipoEstagioResponse paraResponse(TipoEstagio tipoEstagio) {
        return TipoEstagioResponse.builder()
                .id(tipoEstagio.getId())
                .nome(tipoEstagio.getNome())
                .descricao(tipoEstagio.getDescricao())
                .cargaHorariaNecessaria(tipoEstagio.getCargaHorariaNecessaria())
                .jornadaSemanal(tipoEstagio.getJornadaSemanal())
                .build();
    }
}
