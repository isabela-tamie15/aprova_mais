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
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.TipoEstagioRepository;

import java.util.List;

/*
   Esse service é quem cuida do cadastro dos tipos de estágio pelo coordenador
   (RF14, RN30, RN31). A lista que o aluno vê no cadastro do estágio continua no
   EstagioService; aqui fica só o lado da coordenação
*/
@Service
@RequiredArgsConstructor
public class TipoEstagioService {

    private static final Logger log = LoggerFactory.getLogger(TipoEstagioService.class);

    private final TipoEstagioRepository tipoEstagioRepository;
    private final CursoService cursoService;
    private final AuditoriaService auditoriaService;

    // Esse é o método que lista os tipos de um curso, desde que o curso seja do coordenador logado
    @Transactional(readOnly = true)
    public List<TipoEstagioResponse> listar(Long cursoId, String emailCoordenador) {
        Curso curso = cursoService.buscarCursoDoCoordenador(cursoId, emailCoordenador);
        return tipoEstagioRepository.findByCursoIdOrderByNomeAsc(curso.getId())
                .stream()
                .map(this::paraResponse)
                .toList();
    }

    // Esse é o método que cadastra um tipo de estágio num curso do coordenador
    @Transactional
    public TipoEstagioResponse cadastrar(String emailCoordenador, TipoEstagioRequest request, String ipOrigem) {
        Curso curso = cursoService.buscarCursoDoCoordenador(request.getCursoId(), emailCoordenador);
        String nome = request.getNome().trim();

        if (tipoEstagioRepository.existsByCursoIdAndNomeIgnoreCase(curso.getId(), nome)) {
            throw new IllegalStateException("Já existe o tipo de estágio " + nome + " neste curso");
        }

        TipoEstagio tipo = new TipoEstagio();
        tipo.setCurso(curso);
        preencher(tipo, request, nome);
        TipoEstagio salvo = tipoEstagioRepository.save(tipo);

        auditoriaService.registrar(curso.getCoordenador(), "TIPO_ESTAGIO_CADASTRADO",
                "Tipo de estágio id=" + salvo.getId() + " (" + nome + ") no curso id=" + curso.getId(),
                ipOrigem, true);
        log.info("[TIPO ESTÁGIO] Tipo {} cadastrado no curso {} por {}", salvo.getId(), curso.getId(), emailCoordenador);

        return paraResponse(salvo);
    }

    /*
       Esse é o método que altera um tipo de estágio. Mudar a carga ou a jornada
       não afeta os estágios já cadastrados, porque eles guardam uma cópia dos
       valores (RN31). Só os estágios cadastrados depois usam os valores novos
    */
    @Transactional
    public TipoEstagioResponse atualizar(Long tipoId, String emailCoordenador, TipoEstagioRequest request,
                                         String ipOrigem) {
        TipoEstagio tipo = tipoEstagioRepository.findByIdAndCursoCoordenadorEmail(tipoId, emailCoordenador)
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de estágio não encontrado"));

        if (!tipo.getCurso().getId().equals(request.getCursoId())) {
            throw new IllegalStateException("Um tipo de estágio não pode mudar de curso");
        }

        String nome = request.getNome().trim();
        if (tipoEstagioRepository.existsByCursoIdAndNomeIgnoreCaseAndIdNot(tipo.getCurso().getId(), nome, tipoId)) {
            throw new IllegalStateException("Já existe o tipo de estágio " + nome + " neste curso");
        }

        preencher(tipo, request, nome);
        TipoEstagio salvo = tipoEstagioRepository.save(tipo);

        auditoriaService.registrar(tipo.getCurso().getCoordenador(), "TIPO_ESTAGIO_ALTERADO",
                "Tipo de estágio id=" + tipoId + " alterado (carga " + request.getCargaHorariaNecessaria()
                        + " h, jornada " + request.getJornadaSemanal() + " h)", ipOrigem, true);
        log.info("[TIPO ESTÁGIO] Tipo {} alterado por {}", tipoId, emailCoordenador);

        return paraResponse(salvo);
    }

    private void preencher(TipoEstagio tipo, TipoEstagioRequest request, String nome) {
        tipo.setNome(nome);
        tipo.setDescricao(request.getDescricao() == null || request.getDescricao().isBlank()
                ? null
                : request.getDescricao().trim());
        tipo.setCargaHorariaNecessaria(request.getCargaHorariaNecessaria());
        tipo.setJornadaSemanal(request.getJornadaSemanal());
    }

    private TipoEstagioResponse paraResponse(TipoEstagio tipo) {
        return TipoEstagioResponse.builder()
                .id(tipo.getId())
                .nome(tipo.getNome())
                .descricao(tipo.getDescricao())
                .cargaHorariaNecessaria(tipo.getCargaHorariaNecessaria())
                .jornadaSemanal(tipo.getJornadaSemanal())
                .build();
    }
}
