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

// Esse service é quem cuida de tudo relacionado ao estágio, desde o cadastro até a aprovação pelo orientador
@Service
@RequiredArgsConstructor
public class EstagioService {

    private static final Logger log = LoggerFactory.getLogger(EstagioService.class);

    private final EstagioRepository estagioRepository;
    private final MatriculaRepository matriculaRepository;
    private final AlunoRepository alunoRepository;
    private final TipoEstagioRepository tipoEstagioRepository;
    private final OrientadorTurmaRepository orientadorTurmaRepository;
    private final AuditoriaService auditoriaService;
    private final UsuarioRepository usuarioRepository;

    // Esse é o método que busca o estágio ativo de um aluno, usado quando a gente precisa garantir que existe
    @Transactional(readOnly = true)
    public EstagioResponse buscarEstagioAtivo(String emailAluno) {

        /*
           Primeiro acha a matrícula ativa do aluno, depois o estágio ativo
           vinculado a essa matrícula. Se faltar qualquer um dos dois, já
           para aqui com 404, porque não dá pra continuar sem os dois
        */
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

    // Esse é o método que lista todos os tipos de estágio disponíveis pra escolher no cadastro
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

    // Esse é o método que busca o estágio do aluno, mas devolve Optional porque o aluno pode não ter nenhum ainda
    @Transactional(readOnly = true)
    public Optional<EstagioResponse> buscarEstagioDoAluno(String emailAluno) {
        Aluno aluno = buscarAlunoPorEmail(emailAluno);
        Matricula matricula = buscarMatriculaAtiva(aluno);

        return estagioRepository.findByMatriculaId(matricula.getId())
                .map(this::paraResponse);
    }

    // Esse é o método que o aluno usa pra cadastrar um estágio novo ou atualizar um que já existe
    @Transactional
    public EstagioResponse cadastrarOuAtualizarEstagio(String emailAluno,
                                                       EstagioCadastroRequest request,
                                                       String ipOrigem) {
        Aluno aluno = buscarAlunoPorEmail(emailAluno);
        Matricula matricula = buscarMatriculaAtiva(aluno);

        TipoEstagio tipoEstagio = tipoEstagioRepository.findById(request.getTipoEstagioId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Perfil de estágio não encontrado"));

        Optional<Estagio> estagioExistente =
                estagioRepository.findByMatriculaId(matricula.getId());

        /*
           Se já existe um estágio pra essa matrícula, a gente reaproveita
           o registro e só atualiza os campos. Mas se ele tiver PENDENTE,
           bloqueia, porque não faz sentido o aluno mudar um estágio que
           já tá esperando o orientador analisar
        */
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

        // Limpa qualquer justificativa de rejeição anterior, já que o aluno tá reenviando
        estagio.setJustificativaRejeicao(null);

        /*
           Tenta achar o orientador da turma do aluno. Se a turma ainda não
           tem orientador vinculado, deixa null mesmo, o estágio vai ficar
           pendente até alguém atribuir
        */
        Orientador orientador = orientadorTurmaRepository
                .findFirstByTurmaId(matricula.getTurma().getId())
                .map(OrientadorTurma::getOrientador)
                .orElse(null);
        estagio.setOrientador(orientador);

        estagioRepository.save(estagio);

        /*
           Recarrega o estágio depois de salvar. É meio estranho à primeira
           vista, mas é porque na hora de montar a response a gente acessa
           relações que só ficam disponíveis quando o Hibernate carrega de
           novo do banco
        */
        Estagio recarregado = estagioRepository.findById(estagio.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Estágio não encontrado"));

        auditoriaService.registrar(
                aluno,
                "ESTAGIO_CADASTRADO",
                "Estágio cadastrado para empresa: " + request.getNomeEmpresa(),
                ipOrigem,
                true
        );

        log.info("[ESTÁGIO] Estágio cadastrado/atualizado para aluno: {} - Tipo: {}",
                emailAluno, tipoEstagio.getNome());

        return paraResponse(recarregado);
    }

    // Esse é o método que lista os estágios pendentes de um orientador, incluindo os que ele já rejeitou
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

    // Esse é o método que o orientador usa pra aprovar um estágio pendente
    @Transactional
    public EstagioResponse aprovar(Long estagioId, String emailOrientador, String ipOrigem) {
        Estagio estagio = buscarEstagioDoOrientador(estagioId, emailOrientador);

        // Só faz sentido aprovar se estiver pendente, senão é sinal que alguém já mexeu
        if (estagio.getStatus() != StatusEstagio.PENDENTE) {
            throw new IllegalStateException("Apenas estágios pendentes podem ser aprovados");
        }

        estagio.setStatus(StatusEstagio.ATIVO);
        estagio.setJustificativaRejeicao(null);

        Estagio salvo = estagioRepository.save(estagio);

        /*
           Busca o usuário do orientador só pra registrar na auditoria. Se
           não achar, não faz nada, porque a aprovação em si já foi feita
           e a auditoria não pode travar o fluxo
        */
        Usuario orientador = usuarioRepository.findByEmail(emailOrientador)
                .orElse(null);

        if (orientador != null) {
            auditoriaService.registrar(
                    orientador,
                    "ESTAGIO_APROVADO",
                    "Estágio id=" + estagioId + " aprovado para aluno: "
                            + estagio.getMatricula().getAluno().getNome(),
                    ipOrigem,
                    true
            );
        }

        log.info("[ESTÁGIO] Estágio {} aprovado pelo orientador: {}", estagioId, emailOrientador);
        return paraResponse(salvo);
    }

    // Esse é o método que o orientador usa pra rejeitar um estágio, exige uma justificativa
    @Transactional
    public EstagioResponse rejeitar(Long estagioId, String emailOrientador,
                                    String justificativa, String ipOrigem) {
        Estagio estagio = buscarEstagioDoOrientador(estagioId, emailOrientador);

        if (estagio.getStatus() != StatusEstagio.PENDENTE) {
            throw new IllegalStateException("Apenas estágios pendentes podem ser rejeitados");
        }

        estagio.setStatus(StatusEstagio.REJEITADO);
        estagio.setJustificativaRejeicao(justificativa);

        Estagio salvo = estagioRepository.save(estagio);

        // Mesma ideia do aprovar, auditoria não pode travar o fluxo
        Usuario orientador = usuarioRepository.findByEmail(emailOrientador)
                .orElse(null);

        if (orientador != null) {
            auditoriaService.registrar(
                    orientador,
                    "ESTAGIO_REJEITADO",
                    "Estágio id=" + estagioId + " rejeitado com justificativa",
                    ipOrigem,
                    true
            );
        }

        log.info("[ESTÁGIO] Estágio {} rejeitado pelo orientador: {}", estagioId, emailOrientador);
        return paraResponse(salvo);
    }

    // métodos auxiliares/universais

    /*
       Busca o estágio só se ele estiver vinculado ao orientador autenticado.
       Se não estiver, devolve 404 em vez de 403 de propósito, assim um
       orientador não consegue descobrir se um estágio existe só tentando
       acessar ids aleatórios e vendo a diferença entre os dois erros
    */
    private Estagio buscarEstagioDoOrientador(Long estagioId, String emailOrientador) {
        return estagioRepository.findByIdAndOrientadorEmail(estagioId, emailOrientador)
                .orElseThrow(() -> {
                    log.warn("[ESTÁGIO] Orientador {} tentou acessar estágio {} fora da sua responsabilidade",
                            emailOrientador, estagioId);
                    return new ResourceNotFoundException("Estágio não encontrado");
                });
    }

    // Busca o aluno pelo e-mail, usado como primeiro passo em vários fluxos
    private Aluno buscarAlunoPorEmail(String email) {
        return alunoRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Aluno não encontrado"));
    }

    // Busca a matrícula ativa do aluno, usada como base pra chegar no estágio
    private Matricula buscarMatriculaAtiva(Aluno aluno) {
        return matriculaRepository
                .findFirstByAlunoIdAndStatus(aluno.getId(), StatusMatricula.ATIVA)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aluno não possui matrícula ativa"));
    }

    // Converte a entidade Estagio pro DTO que vai ser devolvido pro frontend
    private EstagioResponse paraResponse(Estagio estagio) {
        return EstagioResponse.builder()
                .id(estagio.getId())
                .status(estagio.getStatus().name())
                .dataInicio(estagio.getDataInicio())
                .nomeTipoEstagio(estagio.getTipoEstagio().getNome())
                .cargaHorariaNecessaria(estagio.getCargaHorariaNecessaria())
                .nomeAluno(estagio.getMatricula().getAluno().getNome())
                .nomeEmpresa(estagio.getNomeEmpresa())
                .nomeOrientador(estagio.getOrientador() != null
                        ? estagio.getOrientador().getNome()
                        : null)
                .justificativaRejeicao(estagio.getJustificativaRejeicao())
                .build();
    }
}