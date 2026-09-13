package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.dto.EstagioResponse;
import tcc.ges.aprovamais.dto.EstagioCadastroRequest;
import tcc.ges.aprovamais.dto.TipoEstagioResponse;
import tcc.ges.aprovamais.entity.*;
import tcc.ges.aprovamais.entity.enums.StatusEstagio;
import tcc.ges.aprovamais.entity.enums.StatusMatricula;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EstagioService {

    private static final Logger log = LoggerFactory.getLogger(EstagioService.class);

    private final EstagioRepository estagioRepository;
    private final MatriculaRepository matriculaRepository;
    private final AlunoRepository alunoRepository;
    private final TipoEstagioRepository tipoEstagioRepository;
    private final OrientadorTurmaRepository orientadorTurmaRepository;

    // Leo - trilha personalizada
    @Transactional(readOnly = true)
    public EstagioResponse buscarEstagioAtivo(String emailAluno) {
        Matricula matricula = matriculaRepository
                .findFirstByAlunoEmailAndStatus(emailAluno, StatusMatricula.ATIVA)
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

    //Isa - cadastro e validação de estaágio
    @Transactional(readOnly = true)
    public List<TipoEstagioResponse> listarTiposEstagioDisponiveis() {
        return tipoEstagioRepository.findAll()
                .stream()
                .map(tipo -> TipoEstagioResponse.builder()
                        .id(tipo.getId())
                        .nome(tipo.getNome())
                        .descricao(tipo.getDescricao())
                        .cargaHorariaNecessaria(tipo.getCargaHorariaNecessaria())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<EstagioResponse> buscarEstagioDoAluno(String emailAluno) {
        Aluno aluno = buscarAlunoPorEmail(emailAluno);
        Matricula matricula = buscarMatriculaAtiva(aluno);

        return estagioRepository.findByMatriculaId(matricula.getId())
                .map(this::paraResponse);
    }

    @Transactional
    public EstagioResponse cadastrarOuAtualizarEstagio(String emailAluno,
                                                       EstagioCadastroRequest request) {
        Aluno aluno = buscarAlunoPorEmail(emailAluno);
        Matricula matricula = buscarMatriculaAtiva(aluno);

        TipoEstagio tipoEstagio = tipoEstagioRepository.findById(request.getTipoEstagioId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Perfil de estágio não encontrado"));

        Optional<Estagio> estagioExistente =
                estagioRepository.findByMatriculaId(matricula.getId());

        Estagio estagio;
        if (estagioExistente.isPresent()) {
            estagio = estagioExistente.get();
            if (estagio.getStatus() == StatusEstagio.PENDENTE) {
                throw new IllegalStateException(
                        "Já existe uma solicitação aguardando aprovação do orientador");
            }
        } else {
            estagio = new Estagio();
            estagio.setMatricula(matricula);
        }

        estagio.setTipoEstagio(tipoEstagio);
        estagio.setDataInicio(request.getDataInicio());
        estagio.setNomeEmpresa(request.getNomeEmpresa());
        estagio.setCargaHorariaNecessaria(tipoEstagio.getCargaHorariaNecessaria());
        estagio.setStatus(StatusEstagio.PENDENTE);
        estagio.setJustificativaRejeicao(null);

        Orientador orientador = orientadorTurmaRepository
                .findFirstByTurmaId(matricula.getTurma().getId())
                .map(OrientadorTurma::getOrientador)
                .orElse(null);
        estagio.setOrientador(orientador);

        estagioRepository.save(estagio);

        Estagio recarregado = estagioRepository.findById(estagio.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Estágio não encontrado"));

        log.info("[ESTÁGIO] Estágio cadastrado/atualizado para aluno: {} - Tipo: {}",
                emailAluno, tipoEstagio.getNome());

        return paraResponse(recarregado);
    }

    @Transactional(readOnly = true)
    public List<EstagioResponse> listarPendentes(String emailOrientador) {
        return estagioRepository
                .findByOrientadorEmailAndStatusIn(
                        emailOrientador,
                        List.of(StatusEstagio.PENDENTE, StatusEstagio.REJEITADO)
                )
                .stream()
                .map(this::paraResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public EstagioResponse aprovar(Long estagioId, String emailOrientador) {
        Estagio estagio = estagioRepository.findById(estagioId)
                .orElseThrow(() -> new ResourceNotFoundException("Estágio não encontrado"));

        if (estagio.getStatus() != StatusEstagio.PENDENTE) {
            throw new IllegalStateException("Apenas estágios pendentes podem ser aprovados");
        }

        estagio.setStatus(StatusEstagio.ATIVO);
        estagio.setJustificativaRejeicao(null);

        Estagio salvo = estagioRepository.save(estagio);
        log.info("[ESTÁGIO] Estágio {} aprovado pelo orientador: {}", estagioId, emailOrientador);
        return paraResponse(salvo);
    }

    @Transactional
    public EstagioResponse rejeitar(Long estagioId, String emailOrientador,
                                    String justificativa) {
        Estagio estagio = estagioRepository.findById(estagioId)
                .orElseThrow(() -> new ResourceNotFoundException("Estágio não encontrado"));

        if (estagio.getStatus() != StatusEstagio.PENDENTE) {
            throw new IllegalStateException("Apenas estágios pendentes podem ser rejeitados");
        }

        estagio.setStatus(StatusEstagio.REJEITADO);
        estagio.setJustificativaRejeicao(justificativa);

        Estagio salvo = estagioRepository.save(estagio);
        log.info("[ESTÁGIO] Estágio {} rejeitado pelo orientador: {}", estagioId, emailOrientador);
        return paraResponse(salvo);
    }

    //métodos auxiliares/universais

    private Aluno buscarAlunoPorEmail(String email) {
        return alunoRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Aluno não encontrado"));
    }

    private Matricula buscarMatriculaAtiva(Aluno aluno) {
        return matriculaRepository
                .findFirstByAlunoIdAndStatus(aluno.getId(), StatusMatricula.ATIVA)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aluno não possui matrícula ativa"));
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