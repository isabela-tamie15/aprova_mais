package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.dto.ConsultaCnpjResponse;
import tcc.ges.aprovamais.dto.EstagioCadastroRequest;
import tcc.ges.aprovamais.dto.EstagioResponse;
import tcc.ges.aprovamais.dto.TipoEstagioResponse;
import tcc.ges.aprovamais.dto.ValidacaoEstagioResponse;
import tcc.ges.aprovamais.entity.*;
import tcc.ges.aprovamais.entity.enums.StatusEntrega;
import tcc.ges.aprovamais.entity.enums.StatusEstagio;
import tcc.ges.aprovamais.entity.enums.StatusMatricula;
import tcc.ges.aprovamais.exception.RegraNegocioException;
import tcc.ges.aprovamais.external.EmailService;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

// Cadastro, consulta e validação dos estágios
@Service
@RequiredArgsConstructor
public class EstagioService {

    private static final Logger log = LoggerFactory.getLogger(EstagioService.class);

    // Status de estágio em aberto. Cada matrícula pode ter só um estágio nesses status
    public static final Set<StatusEstagio> STATUS_EM_ABERTO = EnumSet.of(
            StatusEstagio.PENDENTE, StatusEstagio.ATIVO, StatusEstagio.REJEITADO, StatusEstagio.SUSPENSO);

    private final EstagioRepository estagioRepository;
    private final MatriculaRepository matriculaRepository;
    private final AlunoRepository alunoRepository;
    private final TipoEstagioRepository tipoEstagioRepository;
    private final OrientadorTurmaRepository orientadorTurmaRepository;
    private final AuditoriaService auditoriaService;
    private final UsuarioRepository usuarioRepository;
    private final ValidacaoEstagioRepository validacaoEstagioRepository;

    // Contagem das tarefas da trilha do tipo de estágio e das entregas aprovadas do aluno
    private final TrilhaRepository trilhaRepository;
    private final TarefaRepository tarefaRepository;
    private final EntregaRepository entregaRepository;

    private final EmailService emailService;

    @Value("${app.url}")
    private String appUrl;

    // Busca o estágio ativo do aluno. Sem matrícula ou sem estágio ativo, responde 404
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

    // Lista os tipos de estágio que o aluno pode escolher no cadastro, só os do curso da matrícula ativa dele
    @Transactional(readOnly = true)
    public List<TipoEstagioResponse> listarTiposEstagioDisponiveis(String emailAluno) {
        Matricula matricula = buscarMatriculaAtiva(buscarAlunoPorEmail(emailAluno));

        List<TipoEstagio> tiposDoCurso = tipoEstagioRepository.findByCursoIdOrderByNomeAsc(idDoCurso(matricula));

        List<TipoEstagioResponse> respostas = new ArrayList<>();
        for (TipoEstagio tipoEstagio : tiposDoCurso) {
            respostas.add(TipoEstagioResponse.builder()
                    .id(tipoEstagio.getId())
                    .nome(tipoEstagio.getNome())
                    .descricao(tipoEstagio.getDescricao())
                    .cargaHorariaNecessaria(tipoEstagio.getCargaHorariaNecessaria())
                    .jornadaSemanal(tipoEstagio.getJornadaSemanal())
                    .build());
        }
        return respostas;
    }

    // Busca o estágio em aberto do aluno, que pode ainda não existir
    @Transactional(readOnly = true)
    public Optional<EstagioResponse> buscarEstagioDoAluno(String emailAluno) {
        Aluno aluno = buscarAlunoPorEmail(emailAluno);
        Matricula matricula = buscarMatriculaAtiva(aluno);

        // Sem estágio em aberto, devolvemos vazio, e a tela mostra o formulário de cadastro
        Optional<Estagio> estagioEmAberto = buscarEstagioEmAberto(matricula);
        if (estagioEmAberto.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(paraResponse(estagioEmAberto.get()));
    }

    // Lista todos os estágios da matrícula do aluno, inclusive os concluídos e encerrados
    @Transactional(readOnly = true)
    public List<EstagioResponse> listarEstagiosDoAluno(String emailAluno) {
        Matricula matricula = buscarMatriculaAtiva(buscarAlunoPorEmail(emailAluno));

        List<Estagio> estagiosDaMatricula = estagioRepository.findByMatriculaIdOrderByIdAsc(matricula.getId());

        // Transforma cada estágio do banco na resposta que vai para a tela
        List<EstagioResponse> respostas = new ArrayList<>();
        for (Estagio estagio : estagiosDaMatricula) {
            respostas.add(paraResponse(estagio));
        }
        return respostas;
    }

    /*
       Cadastra um estágio novo ou reenvia o rejeitado. A consulta do CNPJ na BrasilAPI chega pronta
       do controller, para essa chamada externa não prender a transação e a conexão do banco
    */
    @Transactional
    public EstagioResponse cadastrarOuAtualizarEstagio(String emailAluno,
                                                       EstagioCadastroRequest request,
                                                       ConsultaCnpjResponse consultaNaReceita,
                                                       String ipOrigem) {
        Aluno aluno = buscarAlunoPorEmail(emailAluno);
        Matricula matricula = buscarMatriculaAtiva(aluno);

        /*
           Busca o tipo já filtrando pelo curso do aluno. Um tipo de outro curso recebe a mesma resposta
           de um id que não existe
        */
        TipoEstagio tipoEstagio = tipoEstagioRepository
                .findByIdAndCursoId(request.getTipoEstagioId(), idDoCurso(matricula))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Perfil de estágio não encontrado"));

        Optional<Estagio> estagioExistente = buscarEstagioEmAberto(matricula);

        /*
           Com um estágio em aberto, só o rejeitado pode ser reenviado, e o registro é reaproveitado.
           Sem nenhum em aberto, um novo é criado e os antigos ficam no histórico
        */
        Estagio estagio;
        if (estagioExistente.isPresent()) {
            estagio = estagioExistente.get();
            validarReenvio(estagio);
        } else {
            // Depois da conclusão, a matrícula não recebe outro estágio
            if (estagioRepository.existsByMatriculaIdAndStatus(matricula.getId(), StatusEstagio.CONCLUIDO)) {
                throw new RegraNegocioException("Seu estágio desta matrícula já foi concluído");
            }
            estagio = new Estagio();
            estagio.setMatricula(matricula);
        }

        // Sem jornada não dá para calcular a carga nem a data prevista, então nem deixa cadastrar
        if (tipoEstagio.getJornadaSemanal() == null || tipoEstagio.getJornadaSemanal().signum() <= 0) {
            throw new RegraNegocioException(
                    "Este tipo de estágio está sem jornada semanal cadastrada. Procure a coordenação");
        }

        /*
           Se a Receita disser que o CNPJ não existe, o cadastro é recusado. Se a consulta não foi
           possível, o cadastro segue sem a situação e o orientador confere o CNPJ no termo de compromisso
        */
        String cnpj = ValidadorCnpj.normalizar(request.getCnpjEmpresa());
        if (consultaNaReceita.status() == ConsultaCnpjResponse.Status.NAO_ENCONTRADO) {
            throw new RegraNegocioException(
                    "CNPJ não encontrado na Receita Federal. Confira o número digitado");
        }

        estagio.setTipoEstagio(tipoEstagio);
        estagio.setDataInicio(request.getDataInicio());
        estagio.setNomeEmpresa(request.getNomeEmpresa().trim());
        estagio.setCnpjEmpresa(cnpj);
        estagio.setSituacaoCnpj(consultaNaReceita.situacao());

        // Copia a carga total e a jornada do tipo, porque o aluno não informa horas
        estagio.setCargaHorariaNecessaria(tipoEstagio.getCargaHorariaNecessaria());
        estagio.setJornadaSemanal(tipoEstagio.getJornadaSemanal());

        // Data prevista de conclusão a partir do início, da carga e da jornada
        estagio.setDataPrevistaConclusao(CalculadoraCargaHoraria.calcularDataPrevista(
                estagio.getDataInicio(), estagio.getCargaHorariaNecessaria(), estagio.getJornadaSemanal()));
        estagio.setStatus(StatusEstagio.PENDENTE);

        // Limpa a justificativa de rejeição anterior, já que o aluno está reenviando
        estagio.setJustificativaRejeicao(null);

        /*
           O orientador do estágio é o da turma do aluno. Sem orientador na turma, o estágio espera
           pendente até o coordenador atribuir um, e aí os estágios em aberto da turma passam para ele
        */
        Optional<OrientadorTurma> vinculoDaTurma = orientadorTurmaRepository.findByTurmaId(matricula.getTurma().getId());
        Orientador orientador = null;
        if (vinculoDaTurma.isPresent()) {
            orientador = vinculoDaTurma.get().getOrientador();
        }
        estagio.setOrientador(orientador);

        estagioRepository.save(estagio);

        // Recarrega do banco para trazer as relações usadas na resposta
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

    // Lista os estágios pendentes do orientador, incluindo os que ele já rejeitou
    @Transactional(readOnly = true)
    public List<EstagioResponse> listarPendentes(String emailOrientador) {
        List<Estagio> estagiosParaValidar = estagioRepository.findByOrientadorEmailAndStatusInOrderByAlteradoEmAscIdAsc(
                emailOrientador, List.of(StatusEstagio.PENDENTE, StatusEstagio.REJEITADO));

        // Transforma cada estágio do banco na resposta que vai para a tela de validações
        List<EstagioResponse> respostas = new ArrayList<>();
        for (Estagio estagio : estagiosParaValidar) {
            respostas.add(paraResponse(estagio));
        }
        return respostas;
    }

    // Progresso de um estágio que já foi buscado e conferido por quem chama
    @Transactional(readOnly = true)
    public EstagioResponse montarProgresso(Estagio estagio) {
        return paraResponse(estagio);
    }

    // Lista os estágios ATIVO do orientador, para ele acompanhar as horas e o aviso de prazo de cada aluno
    @Transactional(readOnly = true)
    public List<EstagioResponse> listarAtivosDoOrientador(String emailOrientador) {
        List<Estagio> estagiosAtivos = estagioRepository.findByOrientadorEmailAndStatusInOrderByIdAsc(
                emailOrientador, List.of(StatusEstagio.ATIVO));

        // Transforma cada estágio do banco na resposta que vai para a tela de acompanhamento
        List<EstagioResponse> respostas = new ArrayList<>();
        for (Estagio estagio : estagiosAtivos) {
            respostas.add(paraResponse(estagio));
        }
        return respostas;
    }

    /*
       Aprova um estágio pendente. A dataInicioConfirmada é a data conferida no termo de compromisso.
       Se for diferente da informada pelo aluno, ela passa a valer e a data prevista é recalculada
    */
    @Transactional
    public EstagioResponse aprovar(Long estagioId, String emailOrientador,
                                   LocalDate dataInicioConfirmada, String ipOrigem) {
        Estagio estagio = buscarEstagioDoOrientador(estagioId, emailOrientador);

        // Só faz sentido aprovar se estiver pendente, senão é sinal que alguém já mexeu
        if (estagio.getStatus() != StatusEstagio.PENDENTE) {
            throw new RegraNegocioException("Apenas estágios pendentes podem ser aprovados");
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
        avisarAluno(salvo, true);

        // Usuário do orientador, para o registro na auditoria
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

    // Rejeita um estágio pendente, com justificativa obrigatória
    @Transactional
    public EstagioResponse rejeitar(Long estagioId, String emailOrientador,
                                    String justificativa, String ipOrigem) {
        Estagio estagio = buscarEstagioDoOrientador(estagioId, emailOrientador);

        if (estagio.getStatus() != StatusEstagio.PENDENTE) {
            throw new RegraNegocioException("Apenas estágios pendentes podem ser rejeitados");
        }

        estagio.setStatus(StatusEstagio.REJEITADO);
        estagio.setJustificativaRejeicao(justificativa);

        Estagio salvo = estagioRepository.save(estagio);
        registrarDecisao(salvo, false, justificativa);
        avisarAluno(salvo, false);

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

    // Histórico de decisões do estágio do aluno logado
    @Transactional(readOnly = true)
    public List<ValidacaoEstagioResponse> listarHistoricoDoAluno(String emailAluno) {
        Matricula matricula = buscarMatriculaAtiva(buscarAlunoPorEmail(emailAluno));

        // Aluno sem estágio em aberto recebe uma lista vazia
        Optional<Estagio> estagioEmAberto = buscarEstagioEmAberto(matricula);
        if (estagioEmAberto.isEmpty()) {
            return List.of();
        }
        return listarHistorico(estagioEmAberto.get().getId());
    }

    // Histórico de um estágio para o orientador, só se o estágio for dele
    @Transactional(readOnly = true)
    public List<ValidacaoEstagioResponse> listarHistoricoDoOrientador(Long estagioId, String emailOrientador) {
        Estagio estagio = buscarEstagioDoOrientador(estagioId, emailOrientador);
        return listarHistorico(estagio.getId());
    }

    // métodos auxiliares/universais

    private Optional<Estagio> buscarEstagioEmAberto(Matricula matricula) {
        return estagioRepository.findFirstByMatriculaIdAndStatusInOrderByIdDesc(
                matricula.getId(), STATUS_EM_ABERTO);
    }

    // Só o estágio rejeitado pode ser reenviado, e cada outro status explica o motivo
    private void validarReenvio(Estagio estagio) {
        switch (estagio.getStatus()) {
            case REJEITADO -> {
                // pode reenviar
            }
            case PENDENTE -> throw new RegraNegocioException(
                    "Já existe uma solicitação aguardando aprovação do orientador");
            case ATIVO -> throw new RegraNegocioException(
                    "Seu estágio já foi aprovado e não pode ser reenviado. "
                            + "Para mudar o tipo, faça uma solicitação de troca de modalidade");
            default -> throw new RegraNegocioException(
                    "Seu estágio está suspenso e não pode ser reenviado");
        }
    }

    // Grava a decisão do orientador no histórico. Nunca é sobrescrita, cada decisão é uma linha nova
    private void registrarDecisao(Estagio estagio, boolean aprovado, String justificativa) {
        ValidacaoEstagio validacao = new ValidacaoEstagio();
        validacao.setEstagio(estagio);
        validacao.setOrientador(estagio.getOrientador());
        validacao.setAprovado(aprovado);
        validacao.setJustificativa(justificativa);
        validacaoEstagioRepository.save(validacao);
    }

    // O envio é assíncrono (@Async no EmailService), então uma falha do Resend não desfaz a decisão do orientador
    private void avisarAluno(Estagio estagio, boolean aprovado) {
        Aluno aluno = estagio.getMatricula().getAluno();
        emailService.enviarResultadoValidacaoEstagio(aluno.getEmail(), aluno.getNome(), aprovado, appUrl + "/aluno/estagio");
    }

    private List<ValidacaoEstagioResponse> listarHistorico(Long estagioId) {
        List<ValidacaoEstagio> decisoes = validacaoEstagioRepository.findByEstagioIdOrderByDataDecisaoAscIdAsc(estagioId);

        // Transforma cada decisão do orientador na resposta que vai para o histórico da tela
        List<ValidacaoEstagioResponse> respostas = new ArrayList<>();
        for (ValidacaoEstagio decisao : decisoes) {
            respostas.add(ValidacaoEstagioResponse.builder()
                    .aprovado(decisao.getAprovado())
                    .justificativa(decisao.getJustificativa())
                    .dataDecisao(decisao.getDataDecisao())
                    .nomeOrientador(decisao.getOrientador().getNome())
                    .build());
        }
        return respostas;
    }

    /*
       Busca o estágio só se ele for do orientador autenticado. Responde 404, e não 403, para o
       orientador não descobrir se um estágio existe testando ids aleatórios
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

    // Busca a matrícula ativa do aluno, usada como base para chegar no estágio
    private Matricula buscarMatriculaAtiva(Aluno aluno) {
        return matriculaRepository
                .findFirstByAlunoIdAndStatus(aluno.getId(), StatusMatricula.ATIVA)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aluno não possui matrícula ativa"));
    }

    /*
       Carga cumprida. Conta só no estágio ATIVO, e o CONCLUIDO já tem a carga toda.
       Os outros status ficam com zero
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

    // Converte a entidade Estagio para o DTO que vai ser devolvido para o frontend
    private EstagioResponse paraResponse(Estagio estagio) {

        // Estágios antigos podem não ter a data prevista gravada, então calcula na hora
        LocalDate dataPrevista = estagio.getDataPrevistaConclusao() != null
                ? estagio.getDataPrevistaConclusao()
                : CalculadoraCargaHoraria.calcularDataPrevista(estagio.getDataInicio(),
                estagio.getCargaHorariaNecessaria(), estagio.getJornadaSemanal());

        /*
           A data de encerramento das entregas vem da turma, porque vale para todos os alunos dela.
           O aviso de prazo só aparece no estágio ATIVO, que é quando o aluno faz as entregas
        */
        LocalDate dataEncerramentoEntregas = estagio.getMatricula().getTurma() != null
                ? estagio.getMatricula().getTurma().getDataEncerramentoEntregas()
                : null;
        Long diasParaEncerramento = CalculadoraCargaHoraria.calcularDiasRestantes(
                dataEncerramentoEntregas, LocalDate.now());
        String avisoPrazo = estagio.getStatus() == StatusEstagio.ATIVO
                ? CalculadoraCargaHoraria.definirAvisoPrazo(diasParaEncerramento)
                : null;

        /*
           Atividades da trilha, contadas só no estágio ATIVO. O total são as tarefas da trilha do tipo,
           e as aprovadas são as entregas do estágio com status APROVADA. Sem pendência, o aviso de
           prazo não aparece
        */
        Long atividadesTotal = null;
        Long atividadesAprovadas = null;
        Long atividadesRestantes = null;
        Boolean podeConcluir = null;
        if (estagio.getStatus() == StatusEstagio.ATIVO) {
            // Sem trilha, o aluno ainda não tem tarefas
            atividadesTotal = 0L;
            Optional<Trilha> trilhaDoTipo = trilhaRepository.findByTipoEstagioId(estagio.getTipoEstagio().getId());
            if (trilhaDoTipo.isPresent()) {
                atividadesTotal = (long) tarefaRepository.findByTrilhaIdOrderByOrdem(trilhaDoTipo.get().getId()).size();
            }

            // Entregas do estágio com status APROVADA
            atividadesAprovadas = 0L;
            for (Entrega entrega : entregaRepository.findByEstagioId(estagio.getId())) {
                if (entrega.getStatus() == StatusEntrega.APROVADA) {
                    atividadesAprovadas++;
                }
            }
            atividadesRestantes = PendenciasEstagio.calcularAtividadesRestantes(atividadesTotal, atividadesAprovadas);

            if (!PendenciasEstagio.temPendencia(atividadesTotal, atividadesAprovadas)) {
                avisoPrazo = null;
            }

            podeConcluir = PendenciasEstagio.podeConcluir(calcularCargaCumprida(estagio),
                    estagio.getCargaHorariaNecessaria(), atividadesTotal, atividadesAprovadas);
        }

        // Semanas usadas no cálculo da carga horária, só no estágio ATIVO
        Long semanasContadas = estagio.getStatus() == StatusEstagio.ATIVO
                ? CalculadoraCargaHoraria.calcularSemanasContadas(estagio.getDataInicio(), LocalDate.now())
                : null;

        /*
           Depois que a carga total é atingida, as semanas param de ser contadas. O limite é o número de
           semanas para cumprir a carga com a jornada, arredondado para cima, como 270h com 44h
           semanais que dão 7 semanas
        */
        if (semanasContadas != null && estagio.getCargaHorariaNecessaria() != null
                && estagio.getJornadaSemanal() != null && estagio.getJornadaSemanal().signum() > 0) {
            long semanasNecessarias = estagio.getCargaHorariaNecessaria()
                    .divide(estagio.getJornadaSemanal(), 0, RoundingMode.CEILING)
                    .longValue();
            semanasContadas = Math.min(semanasContadas, semanasNecessarias);
        }

        return EstagioResponse.builder()
                .id(estagio.getId())
                .status(estagio.getStatus().name())
                .motivoEncerramento(estagio.getMotivoEncerramento() != null
                        ? estagio.getMotivoEncerramento().name()
                        : null)
                .dataInicio(estagio.getDataInicio())
                .nomeTipoEstagio(estagio.getTipoEstagio().getNome())
                .cargaHorariaNecessaria(estagio.getCargaHorariaNecessaria())
                .jornadaSemanal(estagio.getJornadaSemanal())
                .cargaHorariaCumprida(calcularCargaCumprida(estagio))
                .dataPrevistaConclusao(dataPrevista)
                .nomeAluno(estagio.getMatricula().getAluno().getNome())
                .nomeEmpresa(estagio.getNomeEmpresa())
                .cnpjEmpresa(estagio.getCnpjEmpresa() != null
                        ? ValidadorCnpj.formatar(estagio.getCnpjEmpresa())
                        : null)
                .situacaoCnpj(estagio.getSituacaoCnpj())
                .nomeOrientador(estagio.getOrientador() != null
                        ? estagio.getOrientador().getNome()
                        : null)
                .justificativaRejeicao(estagio.getJustificativaRejeicao())
                .cargaHorariaRestante(CalculadoraCargaHoraria.calcularCargaRestante(
                        estagio.getCargaHorariaNecessaria(), calcularCargaCumprida(estagio)))
                .dataEncerramentoEntregas(dataEncerramentoEntregas)
                .diasParaEncerramentoEntregas(diasParaEncerramento)
                .avisoPrazo(avisoPrazo)
                .semanasContadas(semanasContadas)
                .atividadesAprovadas(atividadesAprovadas)
                .atividadesTotal(atividadesTotal)
                .atividadesRestantes(atividadesRestantes)
                .podeConcluir(podeConcluir)
                .dataFinalizacao(estagio.getDataFinalizacao())
                .observacaoEncerramento(estagio.getObservacaoEncerramento())
                .build();
    }
}