package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.dto.EstagioResponse;
import tcc.ges.aprovamais.entity.Estagio;
import tcc.ges.aprovamais.entity.Matricula;
import tcc.ges.aprovamais.entity.enums.StatusEstagio;
import tcc.ges.aprovamais.entity.enums.StatusMatricula;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.EstagioRepository;
import tcc.ges.aprovamais.repository.MatriculaRepository;

@Service
@RequiredArgsConstructor
public class EstagioService {

    private static final Logger log = LoggerFactory.getLogger(EstagioService.class);

    private final EstagioRepository estagioRepository;
    private final MatriculaRepository matriculaRepository;

    @Transactional(readOnly = true)
    public EstagioResponse buscarEstagioAtivo(String emailAluno) {

        Matricula matricula = matriculaRepository
                .findFirstByAlunoUsuarioEmailAndStatus(emailAluno, StatusMatricula.ATIVA)
                .orElseThrow(() -> {
                    log.warn("[ESTÁGIO] Matrícula ativa não encontrada para: {}", emailAluno);
                    return new ResourceNotFoundException("Matrícula ativa não encontrada.");
                });

        Estagio estagio = estagioRepository
                .findByMatriculaIdAndStatus(matricula.getId(), StatusEstagio.ATIVO)
                .orElseThrow(() -> {
                    log.warn("[ESTÁGIO] Estágio ativo não encontrado para matrícula: {}",
                            matricula.getId());
                    return new ResourceNotFoundException("Estágio ativo não encontrado.");
                });

        log.info("[ESTÁGIO] Estágio ativo encontrado para aluno: {} - Tipo: {}",
                emailAluno, estagio.getTipoEstagio().getNome());

        return paraResponse(estagio);
    }

    private EstagioResponse paraResponse(Estagio estagio) {
        return EstagioResponse.builder()
                .id(estagio.getId())
                .status(estagio.getStatus().name())
                .dataInicio(estagio.getDataInicio())
                .nomeTipoEstagio(estagio.getTipoEstagio().getNome())
                .cargaHorariaNecessaria(estagio.getCargaHorariaNecessaria())
                .nomeAluno(estagio.getMatricula().getAluno().getNome())
                .nomeOrientador(estagio.getOrientador() != null
                        ? estagio.getOrientador().getNome()
                        : null)
                .justificativaRejeicao(estagio.getJustificativaRejeicao())
                .build();
    }
}