package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.dto.SolicitacaoRequest;
import tcc.ges.aprovamais.dto.SolicitacaoResponse;
import tcc.ges.aprovamais.entity.*;
import tcc.ges.aprovamais.entity.enums.*;
import tcc.ges.aprovamais.exception.DadoInvalidoException;
import tcc.ges.aprovamais.exception.RegraNegocioException;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.external.EmailService;
import tcc.ges.aprovamais.repository.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/*
   Solicitações do aluno sobre o estágio. A troca de modalidade vai direto para a coordenação. O
   trancamento passa pela secretaria e depois pela coordenação, e a reabertura só pela secretaria.
   A exclusão de anexo pode ser decidida pela secretaria ou pela coordenação, quem analisar primeiro
*/
@Service
@RequiredArgsConstructor
public class SolicitacaoService {

    private static final Logger log = LoggerFactory.getLogger(SolicitacaoService.class);

    // Pedidos que ainda esperam uma decisão. Só pode haver um desses por estágio
    public static final Set<StatusSolicitacao> STATUS_EM_ANALISE = EnumSet.of(
            StatusSolicitacao.PENDENTE, StatusSolicitacao.EM_ANALISE_COORDENACAO);

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final SolicitacaoRepository solicitacaoRepository;
    private final EstagioRepository estagioRepository;
    private final MatriculaRepository matriculaRepository;
    private final AlunoRepository alunoRepository;
    private final TipoEstagioRepository tipoEstagioRepository;
    private final OrientadorTurmaRepository orientadorTurmaRepository;
    private final UsuarioRepository usuarioRepository;
    private final EstagioService estagioService;
    private final EntregaService entregaService;
    private final EntregaVersaoRepository entregaVersaoRepository;
    private final CursoService cursoService;
    private final AuditoriaService auditoriaService;
    private final EmailService emailService;

    @Value("${app.url}")
    private String appUrl;

    // Pedidos do aluno

    @Transactional(readOnly = true)
    public List<SolicitacaoResponse> listarDoAluno(String emailAluno) {
        Matricula matricula = estagioService.buscarMatriculaAtual(buscarAluno(emailAluno));
        return paraResponses(solicitacaoRepository.findByEstagioMatriculaIdOrderByCriadoEmDescIdDesc(matricula.getId()));
    }

    // Abre um pedido sobre o estágio em aberto do aluno. Cada tipo tem a sua condição
    @Transactional
    public SolicitacaoResponse criar(String emailAluno, SolicitacaoRequest request, String ipOrigem) {
        Aluno aluno = buscarAluno(emailAluno);
        Matricula matricula = estagioService.buscarMatriculaAtual(aluno);
        Estagio estagio = estagioRepository.findFirstByMatriculaIdAndStatusInOrderByIdDesc(
                        matricula.getId(), EstagioService.STATUS_EM_ABERTO)
                .orElseThrow(() -> new RegraNegocioException("Você não tem um estágio em aberto"));

        if (request.getTipo() == TipoSolicitacao.EXCLUSAO_ANEXO) {
            throw new RegraNegocioException("Peça a exclusão do anexo pela tarefa, na sua trilha");
        }
        if (solicitacaoRepository.existsByEstagioIdAndStatusInAndTipoNot(
                estagio.getId(), STATUS_EM_ANALISE, TipoSolicitacao.EXCLUSAO_ANEXO)) {
            throw new RegraNegocioException(
                    "Já existe uma solicitação em análise para este estágio. Aguarde a decisão ou cancele o pedido");
        }

        Solicitacao solicitacao = new Solicitacao();
        solicitacao.setEstagio(estagio);
        solicitacao.setTipo(request.getTipo());
        solicitacao.setMotivo(request.getMotivo().trim());

        switch (request.getTipo()) {
            case TROCA_MODALIDADE -> {
                exigirStatus(estagio, StatusEstagio.ATIVO, "A troca de modalidade só pode ser pedida com o estágio ativo");
                if (request.getNovoTipoEstagioId() == null) {
                    throw new DadoInvalidoException("Escolha o novo tipo de estágio");
                }
                TipoEstagio novoTipo = tipoEstagioRepository
                        .findByIdAndCursoId(request.getNovoTipoEstagioId(), matricula.getTurma().getCurso().getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Tipo de estágio não encontrado"));
                if (novoTipo.getId().equals(estagio.getTipoEstagio().getId())) {
                    throw new RegraNegocioException("Escolha um tipo de estágio diferente do atual");
                }
                solicitacao.setNovoTipoEstagio(novoTipo);
                // A troca não passa pela secretaria
                solicitacao.setStatus(StatusSolicitacao.EM_ANALISE_COORDENACAO);
            }
            case TRANCAMENTO -> {
                if (matricula.getStatus() != StatusMatricula.ATIVA) {
                    throw new RegraNegocioException("O trancamento só pode ser pedido com a matrícula ativa");
                }
                exigirStatus(estagio, StatusEstagio.ATIVO, "O trancamento só pode ser pedido com o estágio ativo");
                solicitacao.setStatus(StatusSolicitacao.PENDENTE);
            }
            case REABERTURA -> {
                exigirStatus(estagio, StatusEstagio.SUSPENSO, "A reabertura só pode ser pedida com o estágio suspenso");
                LocalDate prazo = buscarPrazoArrependimento(estagio);
                if (prazo == null || hoje().isAfter(prazo)) {
                    throw new RegraNegocioException("O prazo para pedir a reabertura terminou");
                }
                solicitacao.setStatus(StatusSolicitacao.PENDENTE);
            }
            case EXCLUSAO_ANEXO -> {
            }
        }

        Solicitacao salva = solicitacaoRepository.save(solicitacao);
        auditoriaService.registrar(aluno, "SOLICITACAO_CRIADA",
                "Solicitação id=" + salva.getId() + " de " + salva.getTipo() + " para o estágio id=" + estagio.getId(),
                ipOrigem, true);

        if (salva.getTipo() == TipoSolicitacao.TROCA_MODALIDADE) {
            avisarCoordenador(salva, "Nova solicitação de troca de modalidade",
                    "O aluno " + aluno.getNome() + " pediu a troca de modalidade do estágio. A solicitação aguarda a sua análise.");
        }

        log.info("[SOLICITAÇÃO] {} aberta por {} para o estágio {}", salva.getTipo(), emailAluno, estagio.getId());
        return paraResponse(salva);
    }

    /*
       Pedido de exclusão do anexo de uma versão. A partir daqui o anexo fica bloqueado para todos, e
       a entrega fica parada até a decisão da secretaria ou da coordenação
    */
    @Transactional
    public SolicitacaoResponse solicitarExclusaoAnexo(String emailAluno, Long versaoId, String motivo, String ipOrigem) {
        Aluno aluno = buscarAluno(emailAluno);
        EntregaVersao versao = entregaVersaoRepository.findByIdAndEntregaEstagioMatriculaAlunoEmail(versaoId, emailAluno)
                .orElseThrow(() -> new ResourceNotFoundException("Versão não encontrada"));
        Estagio estagio = versao.getEntrega().getEstagio();
        exigirStatus(estagio, StatusEstagio.ATIVO, "A exclusão de anexo só pode ser pedida com o estágio ativo");
        if (!versao.arquivoDisponivel()) {
            throw new RegraNegocioException("Este anexo já foi excluído ou já tem um pedido de exclusão em análise");
        }
        if (entregaVersaoRepository.existsByEntregaIdAndSituacaoArquivo(versao.getEntrega().getId(),
                SituacaoArquivo.EXCLUSAO_EM_ANALISE)) {
            throw new RegraNegocioException("Já existe um pedido de exclusão em análise para esta tarefa");
        }

        versao.setSituacaoArquivo(SituacaoArquivo.EXCLUSAO_EM_ANALISE);
        entregaVersaoRepository.save(versao);

        Solicitacao solicitacao = new Solicitacao();
        solicitacao.setEstagio(estagio);
        solicitacao.setTipo(TipoSolicitacao.EXCLUSAO_ANEXO);
        solicitacao.setStatus(StatusSolicitacao.PENDENTE);
        solicitacao.setVersao(versao);
        solicitacao.setMotivo(motivo.trim());
        Solicitacao salva = solicitacaoRepository.save(solicitacao);

        auditoriaService.registrar(aluno, "EXCLUSAO_ANEXO_SOLICITADA",
                "Solicitação id=" + salva.getId() + " para a versão " + versao.getNumero() + " da entrega id="
                        + versao.getEntrega().getId(), ipOrigem, true);
        avisarCoordenador(salva, "Pedido de exclusão de anexo",
                "O aluno " + aluno.getNome() + " pediu a exclusão de um anexo enviado na tarefa "
                        + versao.getEntrega().getTarefa().getNome() + ". O anexo está bloqueado até a decisão, "
                        + "que pode ser dada pela coordenação ou pela secretaria.");
        log.info("[SOLICITAÇÃO] Exclusão do anexo da versão {} pedida por {}", versao.getId(), emailAluno);
        return paraResponse(salva);
    }

    // O aluno cancela o próprio pedido enquanto ele ainda está em análise
    @Transactional
    public SolicitacaoResponse cancelar(Long solicitacaoId, String emailAluno, String ipOrigem) {
        Aluno aluno = buscarAluno(emailAluno);
        Matricula matricula = estagioService.buscarMatriculaAtual(aluno);
        Solicitacao solicitacao = solicitacaoRepository.findByIdAndEstagioMatriculaId(solicitacaoId, matricula.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Solicitação não encontrada"));

        if (!STATUS_EM_ANALISE.contains(solicitacao.getStatus())) {
            throw new RegraNegocioException("Só é possível cancelar uma solicitação que ainda está em análise");
        }

        solicitacao.setStatus(StatusSolicitacao.CANCELADA);
        liberarAnexo(solicitacao);
        Solicitacao salva = solicitacaoRepository.save(solicitacao);
        auditoriaService.registrar(aluno, "SOLICITACAO_CANCELADA",
                "Solicitação id=" + solicitacaoId + " cancelada pelo aluno", ipOrigem, true);
        return paraResponse(salva);
    }

    // Secretaria

    @Transactional(readOnly = true)
    public List<SolicitacaoResponse> listarDaSecretaria() {
        return paraResponses(solicitacaoRepository.findByTipoInOrderByCriadoEmDescIdDesc(
                List.of(TipoSolicitacao.TRANCAMENTO, TipoSolicitacao.REABERTURA, TipoSolicitacao.EXCLUSAO_ANEXO)));
    }

    /*
       No trancamento, a secretaria aprova a primeira etapa e o pedido segue para a coordenação.
       Na reabertura, a decisão da secretaria é a final
    */
    @Transactional
    public SolicitacaoResponse aprovarNaSecretaria(Long solicitacaoId, String emailSecretaria, String ipOrigem) {
        Usuario secretaria = buscarUsuario(emailSecretaria);
        Solicitacao solicitacao = buscarDaSecretaria(solicitacaoId);

        solicitacao.setAnalistaSecretaria(secretaria);
        solicitacao.setAnalisadaSecretariaEm(OffsetDateTime.now());

        if (solicitacao.getTipo() == TipoSolicitacao.EXCLUSAO_ANEXO) {
            aprovarExclusaoAnexo(solicitacao, secretaria, "pela secretaria");
        } else if (solicitacao.getTipo() == TipoSolicitacao.TRANCAMENTO) {
            exigirStatus(solicitacao.getEstagio(), StatusEstagio.ATIVO, "O estágio não está mais ativo");
            solicitacao.setStatus(StatusSolicitacao.EM_ANALISE_COORDENACAO);
            avisarAluno(solicitacao, "Trancamento validado pela secretaria",
                    "A secretaria validou o seu pedido de trancamento. Agora ele aguarda a decisão da coordenação.");
            avisarCoordenador(solicitacao, "Trancamento aguardando a sua decisão",
                    "A secretaria validou o pedido de trancamento do aluno "
                            + solicitacao.getEstagio().getMatricula().getAluno().getNome() + ", da turma "
                            + solicitacao.getEstagio().getMatricula().getTurma().getIdentificador()
                            + ". O pedido aguarda a sua aprovação final no sistema.");
        } else {
            exigirStatus(solicitacao.getEstagio(), StatusEstagio.SUSPENSO, "O estágio não está mais suspenso");
            solicitacao.setStatus(StatusSolicitacao.APROVADA);
            reabrir(solicitacao);
            avisarAluno(solicitacao, "Reabertura aprovada",
                    "A secretaria aprovou a reabertura. A sua matrícula e o seu estágio voltaram a ficar ativos, com as horas e as entregas mantidas.");
            avisarCoordenador(solicitacao, "Reabertura aprovada pela secretaria",
                    "A secretaria aprovou a reabertura do estágio do aluno "
                            + solicitacao.getEstagio().getMatricula().getAluno().getNome() + ".");
        }

        Solicitacao salva = solicitacaoRepository.save(solicitacao);
        auditoriaService.registrar(secretaria, "SOLICITACAO_APROVADA_SECRETARIA",
                "Solicitação id=" + solicitacaoId + " de " + salva.getTipo() + " aprovada pela secretaria", ipOrigem, true);
        return paraResponse(salva);
    }

    // Reprovação pela secretaria. Uma reabertura reprovada depois do prazo torna o trancamento definitivo na hora
    @Transactional
    public SolicitacaoResponse reprovarNaSecretaria(Long solicitacaoId, String emailSecretaria, String justificativa,
                                                    String ipOrigem) {
        Usuario secretaria = buscarUsuario(emailSecretaria);
        Solicitacao solicitacao = buscarDaSecretaria(solicitacaoId);

        solicitacao.setAnalistaSecretaria(secretaria);
        solicitacao.setAnalisadaSecretariaEm(OffsetDateTime.now());
        solicitacao.setStatus(StatusSolicitacao.REPROVADA);
        solicitacao.setJustificativaReprovacao(justificativa.trim());
        liberarAnexo(solicitacao);
        Solicitacao salva = solicitacaoRepository.save(solicitacao);

        auditoriaService.registrar(secretaria, "SOLICITACAO_REPROVADA",
                "Solicitação id=" + solicitacaoId + " de " + salva.getTipo() + " reprovada pela secretaria", ipOrigem, true);
        avisarAluno(salva, nomeDoTipo(salva.getTipo()) + " reprovado",
                "A secretaria reprovou o seu pedido. Confira a justificativa no sistema.");

        if (salva.getTipo() == TipoSolicitacao.REABERTURA) {
            avisarCoordenador(salva, "Reabertura reprovada pela secretaria",
                    "A secretaria reprovou a reabertura do estágio do aluno "
                            + salva.getEstagio().getMatricula().getAluno().getNome() + ".");
            LocalDate prazo = buscarPrazoArrependimento(salva.getEstagio());
            if (salva.getEstagio().getStatus() == StatusEstagio.SUSPENSO && prazo != null && hoje().isAfter(prazo)) {
                efetivarTrancamento(salva.getEstagio());
            }
        }
        return paraResponse(salva);
    }

    // Coordenação

    @Transactional(readOnly = true)
    public List<SolicitacaoResponse> listarDoCurso(Long cursoId, String emailCoordenador) {
        cursoService.buscarCursoDoCoordenador(cursoId, emailCoordenador);
        return paraResponses(solicitacaoRepository.findByEstagioMatriculaTurmaCursoIdOrderByCriadoEmDescIdDesc(cursoId));
    }

    /*
       Decisão final da coordenação. Na troca, o estágio atual é encerrado e um novo nasce pendente, do
       zero. No trancamento, a matrícula é trancada, o estágio fica suspenso e começa o prazo de arrependimento
    */
    @Transactional
    public SolicitacaoResponse aprovarNaCoordenacao(Long solicitacaoId, String emailCoordenador, String ipOrigem) {
        Usuario coordenador = buscarUsuario(emailCoordenador);
        Solicitacao solicitacao = buscarDaCoordenacao(solicitacaoId, emailCoordenador);
        Estagio estagio = solicitacao.getEstagio();

        if (solicitacao.getTipo() == TipoSolicitacao.EXCLUSAO_ANEXO) {
            solicitacao.setAnalistaCoordenacao(coordenador);
            solicitacao.setAnalisadaCoordenacaoEm(OffsetDateTime.now());
            aprovarExclusaoAnexo(solicitacao, coordenador, "pela coordenação");
            Solicitacao salva = solicitacaoRepository.save(solicitacao);
            auditoriaService.registrar(coordenador, "SOLICITACAO_APROVADA",
                    "Solicitação id=" + solicitacaoId + " de " + salva.getTipo() + " aprovada pela coordenação", ipOrigem, true);
            return paraResponse(salva);
        }
        exigirStatus(estagio, StatusEstagio.ATIVO, "O estágio não está mais ativo");

        solicitacao.setAnalistaCoordenacao(coordenador);
        solicitacao.setAnalisadaCoordenacaoEm(OffsetDateTime.now());
        solicitacao.setStatus(StatusSolicitacao.APROVADA);

        if (solicitacao.getTipo() == TipoSolicitacao.TROCA_MODALIDADE) {
            trocarModalidade(estagio, solicitacao.getNovoTipoEstagio());
            avisarAluno(solicitacao, "Troca de modalidade aprovada",
                    "A coordenação aprovou a troca para " + solicitacao.getNovoTipoEstagio().getNome()
                            + ". O novo estágio começa do zero e aguarda a validação do orientador.");
        } else {
            LocalDate prazo = hoje().plusDays(ParametrosVigentes.valor(ParametroSistema.PRAZO_ARREPENDIMENTO_DIAS));
            solicitacao.setPrazoArrependimento(prazo);
            trancar(estagio);
            avisarAluno(solicitacao, "Trancamento aprovado",
                    "A coordenação aprovou o trancamento. O seu estágio está suspenso, com as horas e as entregas mantidas. "
                            + "Se mudar de ideia, você pode pedir a reabertura até " + prazo.format(FORMATO_DATA) + ".");
        }

        Solicitacao salva = solicitacaoRepository.save(solicitacao);
        auditoriaService.registrar(coordenador, "SOLICITACAO_APROVADA",
                "Solicitação id=" + solicitacaoId + " de " + salva.getTipo() + " aprovada pela coordenação", ipOrigem, true);
        return paraResponse(salva);
    }

    @Transactional
    public SolicitacaoResponse reprovarNaCoordenacao(Long solicitacaoId, String emailCoordenador, String justificativa,
                                                     String ipOrigem) {
        Usuario coordenador = buscarUsuario(emailCoordenador);
        Solicitacao solicitacao = buscarDaCoordenacao(solicitacaoId, emailCoordenador);

        solicitacao.setAnalistaCoordenacao(coordenador);
        solicitacao.setAnalisadaCoordenacaoEm(OffsetDateTime.now());
        solicitacao.setStatus(StatusSolicitacao.REPROVADA);
        solicitacao.setJustificativaReprovacao(justificativa.trim());
        liberarAnexo(solicitacao);
        Solicitacao salva = solicitacaoRepository.save(solicitacao);

        auditoriaService.registrar(coordenador, "SOLICITACAO_REPROVADA",
                "Solicitação id=" + solicitacaoId + " de " + salva.getTipo() + " reprovada pela coordenação", ipOrigem, true);
        avisarAluno(salva, nomeDoTipo(salva.getTipo()) + " reprovado",
                "A coordenação reprovou o seu pedido. Confira a justificativa no sistema.");
        return paraResponse(salva);
    }

    /*
       Todo dia, às 3h15, encerra os estágios suspensos cujo prazo de arrependimento terminou sem pedido
       de reabertura. Um pedido feito dentro do prazo e ainda em análise segura o encerramento
    */
    @Scheduled(cron = "0 15 3 * * *", zone = "America/Sao_Paulo")
    @Transactional
    public void efetivarTrancamentosVencidos() {
        List<Solicitacao> vencidos = solicitacaoRepository.findByTipoAndStatusAndPrazoArrependimentoBeforeAndEstagioStatus(
                TipoSolicitacao.TRANCAMENTO, StatusSolicitacao.APROVADA, hoje(), StatusEstagio.SUSPENSO);
        for (Solicitacao trancamento : vencidos) {
            Estagio estagio = trancamento.getEstagio();
            if (solicitacaoRepository.existsByEstagioIdAndStatusInAndTipoNot(
                    estagio.getId(), STATUS_EM_ANALISE, TipoSolicitacao.EXCLUSAO_ANEXO)) {
                continue;
            }
            efetivarTrancamento(estagio);
        }
    }

    // Prazo da reabertura, para o painel do aluno. Fica nulo quando o estágio não está suspenso
    @Transactional(readOnly = true)
    public LocalDate buscarPrazoArrependimento(Estagio estagio) {
        return solicitacaoRepository.findFirstByEstagioIdAndTipoAndStatusOrderByIdDesc(
                        estagio.getId(), TipoSolicitacao.TRANCAMENTO, StatusSolicitacao.APROVADA)
                .map(Solicitacao::getPrazoArrependimento)
                .orElse(null);
    }

    // Regras dos efeitos de cada decisão

    // O anexo é apagado na hora, sem passar pela tela de ninguém, e o aluno é avisado
    private void aprovarExclusaoAnexo(Solicitacao solicitacao, Usuario quemAprovou, String origem) {
        solicitacao.setStatus(StatusSolicitacao.APROVADA);
        entregaService.excluirAnexo(solicitacao.getVersao(), quemAprovou);
        auditoriaService.registrar(quemAprovou, "ANEXO_EXCLUIDO",
                "Anexo da versão " + solicitacao.getVersao().getNumero() + " da entrega id="
                        + solicitacao.getVersao().getEntrega().getId() + " excluído " + origem, null, true);
        avisarAluno(solicitacao, "Exclusão do anexo aprovada",
                "O pedido de exclusão do anexo da tarefa " + solicitacao.getVersao().getEntrega().getTarefa().getNome()
                        + " foi aprovado, e o arquivo foi apagado. Se a tarefa voltou para você, envie o arquivo correto pela trilha.");
    }

    // Pedido de exclusão reprovado ou cancelado: o anexo volta a ficar disponível
    private void liberarAnexo(Solicitacao solicitacao) {
        if (solicitacao.getTipo() == TipoSolicitacao.EXCLUSAO_ANEXO && solicitacao.getVersao() != null
                && solicitacao.getVersao().getSituacaoArquivo() == SituacaoArquivo.EXCLUSAO_EM_ANALISE) {
            solicitacao.getVersao().setSituacaoArquivo(SituacaoArquivo.DISPONIVEL);
            entregaVersaoRepository.save(solicitacao.getVersao());
        }
    }

    private void trocarModalidade(Estagio atual, TipoEstagio novoTipo) {
        atual.setStatus(StatusEstagio.ENCERRADO);
        atual.setMotivoEncerramento(MotivoEncerramento.TROCA_MODALIDADE);
        atual.setDataFinalizacao(hoje());
        estagioRepository.save(atual);

        // O novo estágio começa do zero, com a carga e a jornada do novo tipo, e espera a validação do orientador
        Estagio novo = new Estagio();
        novo.setMatricula(atual.getMatricula());
        novo.setTipoEstagio(novoTipo);
        novo.setNomeEmpresa(atual.getNomeEmpresa());
        novo.setLocalEmpresa(atual.getLocalEmpresa());
        novo.setCnpjEmpresa(atual.getCnpjEmpresa());
        novo.setSituacaoCnpj(atual.getSituacaoCnpj());
        novo.setDataInicio(hoje());
        novo.setCargaHorariaNecessaria(novoTipo.getCargaHorariaNecessaria());
        novo.setJornadaSemanal(novoTipo.getJornadaSemanal());
        novo.setDataPrevistaConclusao(CalculadoraCargaHoraria.calcularDataPrevista(
                novo.getDataInicio(), novo.getCargaHorariaNecessaria(), novo.getJornadaSemanal()));
        novo.setStatus(StatusEstagio.PENDENTE);
        Optional<OrientadorTurma> vinculo = orientadorTurmaRepository.findByTurmaId(atual.getMatricula().getTurma().getId());
        novo.setOrientador(vinculo.map(OrientadorTurma::getOrientador).orElse(null));
        estagioRepository.save(novo);
    }

    private void trancar(Estagio estagio) {
        Matricula matricula = estagio.getMatricula();
        matricula.setStatus(StatusMatricula.TRANCADA);
        matriculaRepository.save(matricula);

        estagio.setStatus(StatusEstagio.SUSPENSO);
        estagio.setSuspensoDesde(hoje());
        estagioRepository.save(estagio);
    }

    /*
       Volta a matrícula e o estágio a ativos. Os dias de suspensão contam até a data do pedido de
       reabertura, para a demora da análise não prejudicar o aluno, e a data prevista anda o mesmo tanto
    */
    private void reabrir(Solicitacao reabertura) {
        Estagio estagio = reabertura.getEstagio();
        Matricula matricula = estagio.getMatricula();
        matricula.setStatus(StatusMatricula.ATIVA);
        matriculaRepository.save(matricula);

        LocalDate dataDoPedido = reabertura.getCriadoEm().atZoneSameInstant(FUSO).toLocalDate();
        long dias = Math.max(0, ChronoUnit.DAYS.between(estagio.getSuspensoDesde(), dataDoPedido));
        int diasAnteriores = estagio.getDiasSuspensos() != null ? estagio.getDiasSuspensos() : 0;
        estagio.setDiasSuspensos(diasAnteriores + (int) dias);
        estagio.setSuspensoDesde(null);
        estagio.setStatus(StatusEstagio.ATIVO);
        estagio.setDataPrevistaConclusao(CalculadoraCargaHoraria.calcularDataPrevista(
                CalculadoraCargaHoraria.calcularInicioEfetivo(estagio.getDataInicio(), estagio.getDiasSuspensos()),
                estagio.getCargaHorariaNecessaria(), estagio.getJornadaSemanal()));
        estagioRepository.save(estagio);
    }

    // Trancamento definitivo. O estágio é encerrado e a matrícula continua trancada
    private void efetivarTrancamento(Estagio estagio) {
        estagio.setStatus(StatusEstagio.ENCERRADO);
        estagio.setMotivoEncerramento(MotivoEncerramento.TRANCAMENTO);
        estagio.setDataFinalizacao(hoje());
        estagioRepository.save(estagio);

        Aluno aluno = estagio.getMatricula().getAluno();
        auditoriaService.registrar(aluno, "TRANCAMENTO_EFETIVADO",
                "Estágio id=" + estagio.getId() + " encerrado pelo fim do prazo de arrependimento", null, true);
        emailService.enviarAvisoSolicitacao(aluno.getEmail(), aluno.getNome(), "Trancamento definitivo",
                "O prazo para pedir a reabertura terminou, e o trancamento ficou definitivo. O estágio foi encerrado e continua no seu histórico.",
                appUrl + "/aluno/dashboard");
        log.info("[SOLICITAÇÃO] Trancamento definitivo do estágio {}", estagio.getId());
    }

    // Auxiliares

    private void exigirStatus(Estagio estagio, StatusEstagio esperado, String mensagem) {
        if (estagio.getStatus() != esperado) {
            throw new RegraNegocioException(mensagem);
        }
    }

    // A secretaria decide os pedidos de trancamento, de reabertura e de exclusão de anexo que ainda estão na primeira etapa
    private Solicitacao buscarDaSecretaria(Long solicitacaoId) {
        Solicitacao solicitacao = solicitacaoRepository.findById(solicitacaoId)
                .filter(encontrada -> encontrada.getTipo() != TipoSolicitacao.TROCA_MODALIDADE)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitação não encontrada"));
        if (solicitacao.getStatus() != StatusSolicitacao.PENDENTE) {
            throw new RegraNegocioException("Esta solicitação não está aguardando a secretaria");
        }
        return solicitacao;
    }

    /*
       A coordenação só decide os pedidos dos próprios cursos que já estão na etapa dela. A exclusão de
       anexo pode ser decidida direto, sem passar antes pela secretaria
    */
    private Solicitacao buscarDaCoordenacao(Long solicitacaoId, String emailCoordenador) {
        Solicitacao solicitacao = solicitacaoRepository
                .findByIdAndEstagioMatriculaTurmaCursoCoordenadorEmail(solicitacaoId, emailCoordenador)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitação não encontrada"));
        boolean exclusaoPendente = solicitacao.getTipo() == TipoSolicitacao.EXCLUSAO_ANEXO
                && solicitacao.getStatus() == StatusSolicitacao.PENDENTE;
        if (solicitacao.getStatus() != StatusSolicitacao.EM_ANALISE_COORDENACAO && !exclusaoPendente) {
            throw new RegraNegocioException("Esta solicitação não está aguardando a coordenação");
        }
        return solicitacao;
    }

    private Aluno buscarAluno(String email) {
        return alunoRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Aluno não encontrado"));
    }

    private Usuario buscarUsuario(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
    }

    private LocalDate hoje() {
        return LocalDate.now(FUSO);
    }

    private String nomeDoTipo(TipoSolicitacao tipo) {
        return switch (tipo) {
            case TROCA_MODALIDADE -> "Pedido de troca de modalidade";
            case TRANCAMENTO -> "Pedido de trancamento";
            case REABERTURA -> "Pedido de reabertura";
            case EXCLUSAO_ANEXO -> "Pedido de exclusão de anexo";
        };
    }

    private void avisarAluno(Solicitacao solicitacao, String assunto, String texto) {
        Aluno aluno = solicitacao.getEstagio().getMatricula().getAluno();
        emailService.enviarAvisoSolicitacao(aluno.getEmail(), aluno.getNome(), assunto, texto,
                appUrl + "/aluno/solicitacoes");
    }

    // O curso pode estar sem coordenador, e aí não há para quem avisar
    private void avisarCoordenador(Solicitacao solicitacao, String assunto, String texto) {
        Coordenador coordenador = solicitacao.getEstagio().getMatricula().getTurma().getCurso().getCoordenador();
        if (coordenador == null) {
            return;
        }
        emailService.enviarAvisoSolicitacao(coordenador.getEmail(), coordenador.getNome(), assunto, texto,
                appUrl + "/coordenador/solicitacoes");
    }

    private List<SolicitacaoResponse> paraResponses(List<Solicitacao> solicitacoes) {
        List<SolicitacaoResponse> respostas = new ArrayList<>();
        for (Solicitacao solicitacao : solicitacoes) {
            respostas.add(paraResponse(solicitacao));
        }
        return respostas;
    }

    private SolicitacaoResponse paraResponse(Solicitacao solicitacao) {
        Estagio estagio = solicitacao.getEstagio();
        Matricula matricula = estagio.getMatricula();
        return SolicitacaoResponse.builder()
                .id(solicitacao.getId())
                .tipo(solicitacao.getTipo().name())
                .status(solicitacao.getStatus().name())
                .motivo(solicitacao.getMotivo())
                .justificativaReprovacao(solicitacao.getJustificativaReprovacao())
                .estagioId(estagio.getId())
                .nomeAluno(matricula.getAluno().getNome())
                .turma(matricula.getTurma().getIdentificador() + " (" + matricula.getTurma().getSemestre() + ")")
                .nomeCurso(matricula.getTurma().getCurso().getNome())
                .nomeTipoEstagioAtual(estagio.getTipoEstagio().getNome())
                .nomeNovoTipoEstagio(solicitacao.getNovoTipoEstagio() != null ? solicitacao.getNovoTipoEstagio().getNome() : null)
                .nomeTarefa(solicitacao.getVersao() != null ? solicitacao.getVersao().getEntrega().getTarefa().getNome() : null)
                .numeroVersao(solicitacao.getVersao() != null ? solicitacao.getVersao().getNumero() : null)
                .nomeAnalistaSecretaria(solicitacao.getAnalistaSecretaria() != null ? solicitacao.getAnalistaSecretaria().getNome() : null)
                .analisadaSecretariaEm(solicitacao.getAnalisadaSecretariaEm())
                .nomeAnalistaCoordenacao(solicitacao.getAnalistaCoordenacao() != null ? solicitacao.getAnalistaCoordenacao().getNome() : null)
                .analisadaCoordenacaoEm(solicitacao.getAnalisadaCoordenacaoEm())
                .prazoArrependimento(solicitacao.getPrazoArrependimento())
                .criadoEm(solicitacao.getCriadoEm())
                .build();
    }
}
