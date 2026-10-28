package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.dto.CursoRequest;
import tcc.ges.aprovamais.dto.CursoResponse;
import tcc.ges.aprovamais.entity.Coordenador;
import tcc.ges.aprovamais.entity.Curso;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.CoordenadorRepository;
import tcc.ges.aprovamais.repository.CursoRepository;

import java.util.List;

// Esse service é quem cuida do cadastro de cursos pelo coordenador (RF11)
@Service
@RequiredArgsConstructor
public class CursoService {

    private static final Logger log = LoggerFactory.getLogger(CursoService.class);

    private final CursoRepository cursoRepository;
    private final CoordenadorRepository coordenadorRepository;
    private final AuditoriaService auditoriaService;

    // Esse é o método que lista os cursos do coordenador logado, e só os dele (RN21)
    @Transactional(readOnly = true)
    public List<CursoResponse> listarDoCoordenador(String emailCoordenador) {
        return cursoRepository.findByCoordenadorEmailOrderByNomeAsc(emailCoordenador)
                .stream()
                .map(this::paraResponse)
                .toList();
    }

    /*
       Esse é o método que cadastra um curso novo. Quem cadastra vira o
       coordenador do curso (RN20), e o código e-MEC não pode repetir (RN19)
    */
    @Transactional
    public CursoResponse cadastrar(String emailCoordenador, CursoRequest request, String ipOrigem) {
        Coordenador coordenador = buscarCoordenador(emailCoordenador);
        String codigoMec = request.getCodigoMec().trim();

        if (cursoRepository.existsByCodigoMec(codigoMec)) {
            throw new IllegalStateException("Já existe um curso com esse código e-MEC");
        }

        Curso curso = new Curso();
        curso.setNome(request.getNome().trim());
        curso.setCodigoMec(codigoMec);
        curso.setCoordenador(coordenador);
        Curso salvo = cursoRepository.save(curso);

        auditoriaService.registrar(coordenador, "CURSO_CADASTRADO",
                "Curso id=" + salvo.getId() + " cadastrado (e-MEC " + codigoMec + ")", ipOrigem, true);
        log.info("[CURSO] Curso {} cadastrado pelo coordenador {}", salvo.getId(), emailCoordenador);

        return paraResponse(salvo);
    }

    // Esse é o método que altera o nome ou o código de um curso do próprio coordenador
    @Transactional
    public CursoResponse atualizar(Long cursoId, String emailCoordenador, CursoRequest request, String ipOrigem) {
        Curso curso = buscarCursoDoCoordenador(cursoId, emailCoordenador);
        String codigoMec = request.getCodigoMec().trim();

        // Na edição, o próprio curso pode manter o código, por isso o IdNot
        if (cursoRepository.existsByCodigoMecAndIdNot(codigoMec, cursoId)) {
            throw new IllegalStateException("Já existe um curso com esse código e-MEC");
        }

        curso.setNome(request.getNome().trim());
        curso.setCodigoMec(codigoMec);
        Curso salvo = cursoRepository.save(curso);

        auditoriaService.registrar(curso.getCoordenador(), "CURSO_ALTERADO",
                "Curso id=" + cursoId + " alterado", ipOrigem, true);
        log.info("[CURSO] Curso {} alterado pelo coordenador {}", cursoId, emailCoordenador);

        return paraResponse(salvo);
    }

    /*
       Busca o curso só se ele for do coordenador logado. Os cadastros de turma,
       tipo de estágio e trilha usam esse método antes de qualquer coisa, assim a
       regra "só nos próprios cursos" (RN21) fica num lugar só. Curso de outro
       coordenador recebe o mesmo 404 de um id que não existe
    */
    @Transactional(readOnly = true)
    public Curso buscarCursoDoCoordenador(Long cursoId, String emailCoordenador) {
        return cursoRepository.findByIdAndCoordenadorEmail(cursoId, emailCoordenador)
                .orElseThrow(() -> {
                    log.warn("[CURSO] Coordenador {} tentou acessar o curso {}, que não é dele ou não existe",
                            emailCoordenador, cursoId);
                    return new ResourceNotFoundException("Curso não encontrado");
                });
    }

    // Busca o coordenador logado. Se o token for de outro perfil, o SecurityConfig já barrou antes
    private Coordenador buscarCoordenador(String email) {
        return coordenadorRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Coordenador não encontrado"));
    }

    private CursoResponse paraResponse(Curso curso) {
        return CursoResponse.builder()
                .id(curso.getId())
                .nome(curso.getNome())
                .codigoMec(curso.getCodigoMec())
                .build();
    }
}
