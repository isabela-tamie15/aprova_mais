package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.external.ConsultaCnpjService;
import tcc.ges.aprovamais.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

// Esse service é quem cuida de tudo relacionado ao estágio, desde o cadastro até a aprovação pelo orientador
@Service
@RequiredArgsConstructor
public class EstagioService {

    private static final Logger log = LoggerFactory.getLogger(EstagioService.class);

    // Status em que o estágio ainda está em aberto (RN35 e RN36). Só pode existir um assim por matrícula
    static final Set<StatusEstagio> STATUS_EM_ABERTO = EnumSet.of(
            StatusEstagio.PENDENTE, StatusEstagio.ATIVO, StatusEstagio.REJEITADO, StatusEstagio.SUSPENSO);

    private final EstagioRepository estagioRepository;
    private final MatriculaRepository matriculaRepository;
    private final AlunoRepository alunoRepository;
    private final TipoEstagioRepository tipoEstagioRepository;
    private final OrientadorTurmaRepository orientadorTurmaRepository;
    private final AuditoriaService auditoriaService;
    private final UsuarioRepository usuarioRepository;
    private final ValidacaoEstagioRepository validacaoEstagioRepository;
    private final ConsultaCnpjService consultaCnpjService;

    // F2: usados para contar as tarefas da trilha do tipo de estágio e as entregas aprovadas do aluno
    private final TrilhaRepository trilhaRepository;
    private final TarefaRepository tarefaRepository;
    private final EntregaRepository entregaRepository;

    // Esse é o método que busca o estágio ativo de um aluno, usado quando nós precisamos garantir que existe
    @Transactional(readOnly = true)
    public EstagioResponse buscarEstagioAtivo(String emailAluno) {

        /*
           Primeiro acha a matrícula ativa do aluno, depois o estágio ativo
           vinculado a essa matrícula. Se faltar qualquer um dos dois, já
           para aqui com 404, porque não dá para continuar sem os dois
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

        List<TipoEstagio> tiposDoCurso = tipoEstagioRepository.findByCursoIdOrderByNomeAsc(idDoCurso(matricula));

        // Transforma cada tipo de estágio do banco na resposta que vai para a tela de cadastro
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

    // Esse é o método que busca o estágio do aluno, mas devolve Optional porque o aluno pode não ter nenhum ainda
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

    // Esse é o método que lista todos os estágios da matrícula do aluno, inclusive os concluídos e encerrados
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

    // Esse é o método que o aluno usa para cadastrar um estágio novo ou atualizar um que já existe
    @Transactional
    public EstagioResponse cadastrarOuAtualizarEstagio(String emailAluno,
                                                       EstagioCadastroRequest request,
                                                       String ipOrigem) {
        Aluno aluno = buscarAlunoPorEmail(emailAluno);
        Matricula matricula = buscarMatriculaAtiva(aluno);

        /*
           Busca o tipo já filtrando pelo curso do aluno. Se o id for de um tipo
           de outro curso, responde igual a um id que não existe, assim não dá
           para cadastrar com tipo alheio mandando o id direto na API
        */
        TipoEstagio tipoEstagio = tipoEstagioRepository
                .findByIdAndCursoId(request.getTipoEstagioId(), idDoCurso(matricula))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Perfil de estágio não encontrado"));

        Optional<Estagio> estagioExistente = buscarEstagioEmAberto(matricula);

        /*
           Se já existe um estágio em aberto, só dá para reenviar se ele foi
           rejeitado e aí nós reaproveitamos o registro. Em pendente já
           está com o orientador, ativo já foi aprovado e só muda pela solicitação
           de troca de modalidade e SUSPENSO está parado por trancamento.
           Se não tem nenhum em aberto (nunca teve, ou os anteriores foram
           concluídos ou encerrados), cria um novo e os antigos ficam no histórico
        */
        Estagio estagio;
        if (estagioExistente.isPresent()) {
            estagio = estagioExistente.get();
            validarReenvio(estagio);
        } else {
            estagio = new Estagio();
            estagio.setMatricula(matricula);
        }

        // Sem jornada não dá para calcular a carga nem a data prevista, então nem deixa cadastrar
        if (tipoEstagio.getJornadaSemanal() == null || tipoEstagio.getJornadaSemanal().signum() <= 0) {
            throw new IllegalStateException(
                    "Este tipo de estágio está sem jornada semanal cadastrada. Procure a coordenação");
        }

        /*
           Consulta o CNPJ na BrasilAPI (Seção 6.9.2 da monografia). Se a Receita disser que
           o CNPJ não existe, recusamos. Se a consulta não for possível, o cadastro segue sem a
           situação, e o orientador confere o CNPJ no termo de compromisso
        */
        String cnpj = ValidadorCnpj.normalizar(request.getCnpjEmpresa());
        ConsultaCnpjResponse consultaNaReceita = consultaCnpjService.consultar(cnpj);
        if (consultaNaReceita.status() == ConsultaCnpjResponse.Status.NAO_ENCONTRADO) {
            throw new IllegalStateException(
                    "CNPJ não encontrado na Receita Federal. Confira o número digitado");
        }

        estagio.setTipoEstagio(tipoEstagio);
        estagio.setDataInicio(request.getDataInicio());
        estagio.setNomeEmpresa(request.getNomeEmpresa().trim());
        estagio.setCnpjEmpresa(cnpj);
        estagio.setSituacaoCnpj(consultaNaReceita.situacao());

        // RN31: copia a carga total e a jornada do tipo, o aluno não informa horas
        estagio.setCargaHorariaNecessaria(tipoEstagio.getCargaHorariaNecessaria());
        estagio.setJornadaSemanal(tipoEstagio.getJornadaSemanal());

        // RN54: data prevista de conclusão a partir do início, da carga e da jornada
        estagio.setDataPrevistaConclusao(CalculadoraCargaHoraria.calcularDataPrevista(
                estagio.getDataInicio(), estagio.getCargaHorariaNecessaria(), estagio.getJornadaSemanal()));
        estagio.setStatus(StatusEstagio.PENDENTE);

        // Limpa qualquer justificativa de rejeição anterior, já que o aluno está reenviando
        estagio.setJustificativaRejeicao(null);

        /*
           Tenta achar o orientador da turma do aluno. Se a turma ainda não
           tem orientador vinculado, deixa null mesmo, o estágio vai ficar
           pendente até alguém atribuir
        */
        Optional<OrientadorTurma> vinculoDaTurma = orientadorTurmaRepository.findFirstByTurmaId(matricula.getTurma().getId());
        Orientador orientador = null;
        if (vinculoDaTurma.isPresent()) {
            orientador = vinculoDaTurma.get().getOrientador();
        }
        estagio.setOrientador(orientador);

        estagioRepository.save(estagio);

        /*
           Recarrega o estágio depois de salvar. É meio estranho à primeira
           vista, mas é porque na hora de montar a response nós acessamos
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
        List<Estagio> estagiosParaValidar = estagioRepository.findByOrientadorEmailAndStatusIn(
                emailOrientador, List.of(StatusEstagio.PENDENTE, StatusEstagio.REJEITADO));

        // Transforma cada estágio do banco na resposta que vai para a tela de validações
        List<EstagioResponse> respostas = new ArrayList<>();
        for (Estagio estagio : estagiosParaValidar) {
            respostas.add(paraResponse(estagio));
        }
        return respostas;
    }

    // Progresso de um estágio que já foi buscado e conferido por quem chama (visão consolidada do aluno, RF37)
    @Transactional(readOnly = true)
    public EstagioResponse montarProgresso(Estagio estagio) {
        return paraResponse(estagio);
    }

    /*
       F2: lista os estágios ATIVO do orientador, para ele acompanhar as horas
       e o aviso de prazo de cada aluno. Como a consulta filtra pelo e-mail do
       orientador, ele só enxerga os próprios alunos
    */
    @Transactional(readOnly = true)
    public List<EstagioResponse> listarAtivosDoOrientador(String emailOrientador) {
        List<Estagio> estagiosAtivos = estagioRepository.findByOrientadorEmailAndStatusIn(
                emailOrientador, List.of(StatusEstagio.ATIVO));

        // Transforma cada estágio do banco na resposta que vai para a tela de acompanhamento
        List<EstagioResponse> respostas = new ArrayList<>();
        for (Estagio estagio : estagiosAtivos) {
            respostas.add(paraResponse(estagio));
        }
        return respostas;
    }

    /*
       Esse é o método que o orientador usa para aprovar um estágio pendente.
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
           Busca o usuário do orientador só para registrar na auditoria.
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

    // Esse é o método que o orientador usa para rejeitar um estágio, exige uma justificativa
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

        // Aluno sem estágio em aberto não tem histórico para mostrar: devolvemos lista vazia em vez de erro
        Optional<Estagio> estagioEmAberto = buscarEstagioEmAberto(matricula);
        if (estagioEmAberto.isEmpty()) {
            return List.of();
        }
        return listarHistorico(estagioEmAberto.get().getId());
    }

    // Esse é o método que devolve o histórico de um estágio para o orientador, só se o estágio for dele
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

    // é a RN42, só o estágio rejeitado pode ser reenviado, cada outro status explica o motivo
    private void validarReenvio(Estagio estagio) {
        switch (estagio.getStatus()) {
            case REJEITADO -> {
                // pode reenviar
            }
            case PENDENTE -> throw new IllegalStateException(
                    "Já existe uma solicitação aguardando aprovação do orientador");
            case ATIVO -> throw new IllegalStateException(
                    "Seu estágio já foi aprovado e não pode ser reenviado. "
                            + "Para mudar o tipo, faça uma solicitação de troca de modalidade");
            default -> throw new IllegalStateException(
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

    // Busca a matrícula ativa do aluno, usada como base para chegar no estágio
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

    // Converte a entidade Estagio para o DTO que vai ser devolvido para o frontend
    private EstagioResponse paraResponse(Estagio estagio) {

        // Estágios antigos podem não ter a data prevista gravada, então calcula na hora
        LocalDate dataPrevista = estagio.getDataPrevistaConclusao() != null
                ? estagio.getDataPrevistaConclusao()
                : CalculadoraCargaHoraria.calcularDataPrevista(estagio.getDataInicio(),
                estagio.getCargaHorariaNecessaria(), estagio.getJornadaSemanal());

        /*
           F2: a data de encerramento das entregas vem da turma da matrícula, porque
           é a mesma para todos os alunos da turma. Os dias são contados a partir de hoje.
           O aviso de prazo só aparece para o estágio ATIVO, já que é só nele que o aluno
           está fazendo as entregas
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
           F2: atividades da trilha, contadas só no estágio ATIVO, que é quando o aluno faz as entregas.
           O total são as tarefas da trilha do tipo de estágio (se o tipo ainda não tem trilha, o total é zero)
           e as aprovadas são as entregas do estágio com status APROVADA. Quando não há pendência,
           ou seja, todas as tarefas foram aprovadas ou a trilha não tem tarefas, o aviso de prazo não aparece
        */
        Long atividadesTotal = null;
        Long atividadesAprovadas = null;
        Long atividadesRestantes = null;
        if (estagio.getStatus() == StatusEstagio.ATIVO) {
            // Total de tarefas da trilha do tipo de estágio. Sem trilha, o aluno ainda não tem tarefas
            atividadesTotal = 0L;
            Optional<Trilha> trilhaDoTipo = trilhaRepository.findByTipoEstagioId(estagio.getTipoEstagio().getId());
            if (trilhaDoTipo.isPresent()) {
                atividadesTotal = (long) tarefaRepository.findByTrilhaIdOrderByOrdem(trilhaDoTipo.get().getId()).size();
            }

            // Tarefas aprovadas: contamos as entregas do estágio com status APROVADA
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
        }

        // RF22: semanas usadas no cálculo da carga horária, só no estágio ATIVO, que é quando a carga é contada pelo período
        Long semanasContadas = estagio.getStatus() == StatusEstagio.ATIVO
                ? CalculadoraCargaHoraria.calcularSemanasContadas(estagio.getDataInicio(), LocalDate.now())
                : null;

        /*
           F2: depois que a carga total é atingida, as semanas param de ser contadas. O limite são as
           semanas necessárias para cumprir a carga com a jornada do tipo, arredondando para cima
           (270h com 44h semanais são 7 semanas). Assim o cálculo da carga não passa de 7 semanas
           mesmo que o estágio continue ATIVO, esperando as atividades serem aprovadas pelo orientador
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
                .build();
    }
}