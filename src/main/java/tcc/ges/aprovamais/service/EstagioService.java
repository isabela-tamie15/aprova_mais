package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.dto.EstagioResponse;
import tcc.ges.aprovamais.dto.EstagioCadastroRequest;
import tcc.ges.aprovamais.dto.TipoEstagioResponse;
import tcc.ges.aprovamais.dto.ValidacaoEstagioResponse;
import tcc.ges.aprovamais.entity.*;
import tcc.ges.aprovamais.entity.enums.StatusEstagio;
import tcc.ges.aprovamais.entity.enums.StatusMatricula;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.*;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    private final ValidacaoEstagioRepository validacaoEstagioRepository;

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

    /*
       Esse é o método que lista os tipos de estágio que o aluno pode escolher no cadastro.
       Só entram os tipos do curso da matrícula ativa dele (RN30), porque cada curso define os próprios tipos, com carga e jornada próprias
    */
    @Transactional(readOnly = true)
    public List<TipoEstagioResponse> listarTiposEstagioDisponiveis(String emailAluno) {
        Matricula matricula = buscarMatriculaAtiva(buscarAlunoPorEmail(emailAluno));

        return tipoEstagioRepository.findByCursoIdOrderByNomeAsc(idDoCurso(matricula))
                .stream()
                .map(tipo -> TipoEstagioResponse.builder()
                        .id(tipo.getId())
                        .nome(tipo.getNome())
                        .descricao(tipo.getDescricao())
                        .cargaHorariaNecessaria(tipo.getCargaHorariaNecessaria())
                        .jornadaSemanal(tipo.getJornadaSemanal())
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

        /*
           Busca o tipo já filtrando pelo curso do aluno. Se o id for de um tipo
           de outro curso, responde igual a um id que não existe, assim não dá
           pra cadastrar com tipo alheio mandando o id direto na API
        */
        TipoEstagio tipoEstagio = tipoEstagioRepository
                .findByIdAndCursoId(request.getTipoEstagioId(), idDoCurso(matricula))
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

        // Sem jornada não dá pra calcular a carga nem a data prevista, então nem deixa cadastrar
        if (tipoEstagio.getJornadaSemanal() == null || tipoEstagio.getJornadaSemanal().signum() <= 0) {
            throw new IllegalStateException(
                    "Este tipo de estágio está sem jornada semanal cadastrada. Procure a coordenação");
        }

        estagio.setTipoEstagio(tipoEstagio);
        estagio.setDataInicio(request.getDataInicio());
        estagio.setNomeEmpresa(request.getNomeEmpresa());

        // RN31: copia a carga total e a jornada do tipo, o aluno não informa horas
        estagio.setCargaHorariaNecessaria(tipoEstagio.getCargaHorariaNecessaria());
        estagio.setJornadaSemanal(tipoEstagio.getJornadaSemanal());

        // RN54: data prevista de conclusão a partir do início, da carga e da jornada
        estagio.setDataPrevistaConclusao(CalculadoraCargaHoraria.calcularDataPrevista(
                estagio.getDataInicio(), estagio.getCargaHorariaNecessaria(), estagio.getJornadaSemanal()));
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

    /*
       Esse é o método que o orientador usa pra aprovar um estágio pendente.
       A dataInicioConfirmada é a data que ele conferiu no termo de compromisso (RF21).
       Se vier diferente da que o aluno informou, ela passa a valer e a data prevista é recalculada, porque a carga é contada a partir dela
    */
    @Transactional
    public EstagioResponse aprovar(Long estagioId, String emailOrientador,
                                   LocalDate dataInicioConfirmada, String ipOrigem) {
        Estagio estagio = buscarEstagioDoOrientador(estagioId, emailOrientador);

        // Só faz sentido aprovar se estiver pendente, senão é sinal que alguém já mexeu
        if (estagio.getStatus() != StatusEstagio.PENDENTE) {
            throw new IllegalStateException("Apenas estágios pendentes podem ser aprovados");
        }

        if (dataInicioConfirmada != null && !dataInicioConfirmada.equals(estagio.getDataInicio())) {
            log.info("[ESTÁGIO] Orientador ajustou a data de início do estágio {} de {} para {}",
                    estagioId, estagio.getDataInicio(), dataInicioConfirmada);
            estagio.setDataInicio(dataInicioConfirmada);
            estagio.setDataPrevistaConclusao(CalculadoraCargaHoraria.calcularDataPrevista(
                    dataInicioConfirmada, estagio.getCargaHorariaNecessaria(), estagio.getJornadaSemanal()));
        }

        estagio.setStatus(StatusEstagio.ATIVO);
        estagio.setJustificativaRejeicao(null);

        Estagio salvo = estagioRepository.save(estagio);
        registrarDecisao(salvo, true, null);

        /*
           Busca o usuário do orientador só pra registrar na auditoria.
           Se não achar, não faz nada, porque a aprovação em si já foi feita e a auditoria não pode travar o fluxo
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
        registrarDecisao(salvo, false, justificativa);

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

    // Esse é o método que devolve o histórico de decisões do estágio do aluno logado (RF22)
    @Transactional(readOnly = true)
    public List<ValidacaoEstagioResponse> listarHistoricoDoAluno(String emailAluno) {
        Matricula matricula = buscarMatriculaAtiva(buscarAlunoPorEmail(emailAluno));

        // Aluno sem estágio ainda não tem histórico, devolve lista vazia em vez de erro
        return estagioRepository.findByMatriculaId(matricula.getId())
                .map(estagio -> listarHistorico(estagio.getId()))
                .orElse(List.of());
    }

    // Esse é o método que devolve o histórico de um estágio pro orientador, só se o estágio for dele
    @Transactional(readOnly = true)
    public List<ValidacaoEstagioResponse> listarHistoricoDoOrientador(Long estagioId, String emailOrientador) {
        Estagio estagio = buscarEstagioDoOrientador(estagioId, emailOrientador);
        return listarHistorico(estagio.getId());
    }

    // métodos auxiliares/universais

    // Grava a decisão do orientador no histórico. Nunca é sobrescrita, cada decisão é uma linha nova
    private void registrarDecisao(Estagio estagio, boolean aprovado, String justificativa) {
        ValidacaoEstagio validacao = new ValidacaoEstagio();
        validacao.setEstagio(estagio);
        validacao.setOrientador(estagio.getOrientador());
        validacao.setAprovado(aprovado);
        validacao.setJustificativa(justificativa);
        validacaoEstagioRepository.save(validacao);
    }

    private List<ValidacaoEstagioResponse> listarHistorico(Long estagioId) {
        return validacaoEstagioRepository.findByEstagioIdOrderByDataDecisaoAscIdAsc(estagioId)
                .stream()
                .map(v -> ValidacaoEstagioResponse.builder()
                        .aprovado(v.getAprovado())
                        .justificativa(v.getJustificativa())
                        .dataDecisao(v.getDataDecisao())
                        .nomeOrientador(v.getOrientador().getNome())
                        .build())
                .collect(Collectors.toList());
    }

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

    // Curso da matrícula, que é quem define os tipos de estágio disponíveis
    private Long idDoCurso(Matricula matricula) {
        return matricula.getTurma().getCurso().getId();
    }

    // Busca a matrícula ativa do aluno, usada como base pra chegar no estágio
    private Matricula buscarMatriculaAtiva(Aluno aluno) {
        return matriculaRepository
                .findFirstByAlunoIdAndStatus(aluno.getId(), StatusMatricula.ATIVA)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aluno não possui matrícula ativa"));
    }

    /*
       Carga cumprida (RN53). Só conta enquanto o estágio está ATIVO, e o
       CONCLUIDO já tem a carga toda. Pendente, rejeitado, suspenso e
       encerrado ficam com zero por enquanto. Descontar as semanas de
       suspensão depende do histórico de status, que ainda vai ser feito
    */
    private BigDecimal calcularCargaCumprida(Estagio estagio) {
        return switch (estagio.getStatus()) {
            case ATIVO -> CalculadoraCargaHoraria.calcularCargaCumprida(
                    estagio.getDataInicio(), estagio.getCargaHorariaNecessaria(),
                    estagio.getJornadaSemanal(), LocalDate.now());
            case CONCLUIDO -> estagio.getCargaHorariaNecessaria();
            default -> BigDecimal.ZERO;
        };
    }

    // Converte a entidade Estagio pro DTO que vai ser devolvido pro frontend
    private EstagioResponse paraResponse(Estagio estagio) {

        // Estágios antigos podem não ter a data prevista gravada, então calcula na hora
        LocalDate dataPrevista = estagio.getDataPrevistaConclusao() != null
                ? estagio.getDataPrevistaConclusao()
                : CalculadoraCargaHoraria.calcularDataPrevista(estagio.getDataInicio(),
                        estagio.getCargaHorariaNecessaria(), estagio.getJornadaSemanal());

        return EstagioResponse.builder()
                .id(estagio.getId())
                .status(estagio.getStatus().name())
                .dataInicio(estagio.getDataInicio())
                .nomeTipoEstagio(estagio.getTipoEstagio().getNome())
                .cargaHorariaNecessaria(estagio.getCargaHorariaNecessaria())
                .jornadaSemanal(estagio.getJornadaSemanal())
                .cargaHorariaCumprida(calcularCargaCumprida(estagio))
                .dataPrevistaConclusao(dataPrevista)
                .nomeAluno(estagio.getMatricula().getAluno().getNome())
                .nomeEmpresa(estagio.getNomeEmpresa())
                .nomeOrientador(estagio.getOrientador() != null
                        ? estagio.getOrientador().getNome()
                        : null)
                .justificativaRejeicao(estagio.getJustificativaRejeicao())
                .build();
    }
}