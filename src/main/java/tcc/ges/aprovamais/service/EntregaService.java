package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tcc.ges.aprovamais.dto.CriterioChecklistResponse;
import tcc.ges.aprovamais.dto.EntregaDetalheResponse;
import tcc.ges.aprovamais.dto.EntregaVersaoResponse;
import tcc.ges.aprovamais.entity.*;
import tcc.ges.aprovamais.entity.Usuario;
import tcc.ges.aprovamais.entity.enums.SituacaoArquivo;
import tcc.ges.aprovamais.entity.enums.StatusEntrega;
import tcc.ges.aprovamais.entity.enums.StatusEstagio;
import tcc.ges.aprovamais.entity.enums.StatusMatricula;
import tcc.ges.aprovamais.entity.enums.TipoArquivo;
import tcc.ges.aprovamais.exception.DadoInvalidoException;
import tcc.ges.aprovamais.exception.RegraNegocioException;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.*;
import tcc.ges.aprovamais.storage.ArmazenamentoArquivos;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

//Envio das entregas pelo aluno e histórico de versões
@Service
@RequiredArgsConstructor
public class EntregaService {

    private static final Logger log = LoggerFactory.getLogger(EntregaService.class);

    public static final int TAMANHO_MAXIMO_COMENTARIO = 1000;

    private final MatriculaRepository matriculaRepository;
    private final EstagioRepository estagioRepository;
    private final TarefaRepository tarefaRepository;
    private final EntregaRepository entregaRepository;
    private final EntregaVersaoRepository entregaVersaoRepository;
    private final SolicitacaoRepository solicitacaoRepository;
    private final ArmazenamentoArquivos armazenamentoArquivos;
    private final AuditoriaService auditoriaService;
    private final ChecklistService checklistService;

    // Arquivo pronto para o download, vem com nome original, Content-Type e conteúdo
    public record ArquivoEntrega(String nome, String tipoConteudo, byte[] conteudo) {
    }


    /*
       Envia um arquivo para uma tarefa da trilha. Cada envio vira uma versão nova, com o número da
       anterior mais 1, e a entrega volta para ENVIADA no painel do orientador
    */
    @Transactional
    public EntregaDetalheResponse enviar(String emailAluno, Long tarefaId, String nomeOriginal,
                                         byte[] conteudo, String comentario, String ipOrigem) {
        Estagio estagio = buscarEstagioAtivo(emailAluno);
        Tarefa tarefa = buscarTarefaDaTrilha(tarefaId, estagio);

        String comentarioLimpo = comentario == null || comentario.isBlank() ? null : comentario.trim();
        if (comentarioLimpo != null && comentarioLimpo.length() > TAMANHO_MAXIMO_COMENTARIO) {
            throw new DadoInvalidoException("O comentário deve ter no máximo 1000 caracteres.");
        }

        // Reaproveita a entrega se a tarefa já foi enviada antes, ou cria uma nova
        Optional<Entrega> entregaExistente = entregaRepository.findByEstagioIdAndTarefaId(estagio.getId(), tarefa.getId());
        Entrega entrega;
        if (entregaExistente.isPresent()) {
            entrega = entregaExistente.get();
        } else {
            entrega = new Entrega();
            entrega.setEstagio(estagio);
            entrega.setTarefa(tarefa);
        }
        if (entrega.getId() != null
                && entregaVersaoRepository.existsByEntregaIdAndSituacaoArquivo(entrega.getId(), SituacaoArquivo.EXCLUSAO_EM_ANALISE)) {
            throw new RegraNegocioException("Aguarde a decisão sobre o pedido de exclusão do anexo desta tarefa.");
        }
        // Com a entrega em análise ou já aprovada, não dá para mandar outra versão
        if (entrega.getStatus() != null && !podeEnviar(entrega.getStatus())) {
            throw new RegraNegocioException(entrega.getStatus() == StatusEntrega.APROVADA
                    ? "Esta tarefa já foi aprovada pelo orientador."
                    : "Esta tarefa já tem uma entrega aguardando a análise do orientador.");
        }

        TipoArquivo tipoArquivo = ValidadorArquivoEntrega.validar(nomeOriginal, conteudo);
        String nomeArquivo = ValidadorArquivoEntrega.limparNome(nomeOriginal);

        // A primeira entrega da tarefa é a versão 1, e as próximas são a última versão mais 1
        int numero = 1;
        if (entrega.getId() != null) {
            Optional<EntregaVersao> ultimaVersao = entregaVersaoRepository.findFirstByEntregaIdOrderByNumeroDesc(entrega.getId());
            if (ultimaVersao.isPresent()) {
                numero = ultimaVersao.get().getNumero() + 1;
            }
        }


        // A chave é gerada aqui, sem nada do nome que o aluno mandou. O UUID faz com que dois envios nunca caiam no mesmo arquivo
        String chave = "entregas/estagio-" + estagio.getId() + "/tarefa-" + tarefa.getId()
                + "/v" + numero + "-" + UUID.randomUUID() + "." + tipoArquivo.name().toLowerCase(Locale.ROOT);

        entrega.setStatus(StatusEntrega.ENVIADA);
        Entrega salva = entregaRepository.saveAndFlush(entrega);

        EntregaVersao versao = new EntregaVersao();
        versao.setEntrega(salva);
        versao.setNumero(numero);
        versao.setNomeArquivo(nomeArquivo);
        versao.setChaveArquivo(chave);
        versao.setTipoArquivo(tipoArquivo);
        versao.setTamanhoBytes((long) conteudo.length);
        versao.setComentarioAluno(comentarioLimpo);
        versao.setSituacaoArquivo(SituacaoArquivo.DISPONIVEL);

        /*
           O saveAndFlush grava as linhas na hora. Se outro envio da mesma tarefa chegou ao mesmo tempo,
           a restrição única falha aqui e a resposta é 409, antes de o arquivo ser gravado. Se a
           gravação do arquivo falhar, a transação é desfeita e as linhas somem junto
        */
        entregaVersaoRepository.saveAndFlush(versao);
        armazenamentoArquivos.salvar(chave, conteudo, ValidadorArquivoEntrega.tipoConteudo(tipoArquivo));

        auditoriaService.registrar(estagio.getMatricula().getAluno(), "ENTREGA_ENVIADA",
                "Entrega id=" + salva.getId() + " da tarefa id=" + tarefa.getId() + ", versão " + numero,
                ipOrigem, true);
        log.info("[ENTREGA] Versão {} da tarefa {} enviada por {}", numero, tarefa.getId(), emailAluno);

        return montarDetalhe(tarefa, salva);
    }

    // A entrega de uma tarefa com todas as versões. Sem envio ainda, volta PENDENTE e sem versões
    @Transactional(readOnly = true)
    public EntregaDetalheResponse buscarDoAluno(String emailAluno, Long tarefaId) {
        Estagio estagio = buscarEstagioAtivo(emailAluno);
        Tarefa tarefa = buscarTarefaDaTrilha(tarefaId, estagio);
        Entrega entrega = entregaRepository.findByEstagioIdAndTarefaId(estagio.getId(), tarefa.getId())
                .orElse(null);
        return montarDetalhe(tarefa, entrega);
    }

    /*
       Cancela o último envio enquanto o orientador ainda não abriu a entrega. A versão some do
       histórico, e o arquivo é apagado do armazenamento depois que a transação é gravada
    */
    @Transactional
    public EntregaDetalheResponse cancelarEnvio(String emailAluno, Long versaoId, String ipOrigem) {
        Estagio estagio = buscarEstagioAtivo(emailAluno);
        EntregaVersao versao = entregaVersaoRepository.findByIdAndEntregaEstagioMatriculaAlunoEmail(versaoId, emailAluno)
                .filter(encontrada -> encontrada.getEntrega().getEstagio().getId().equals(estagio.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Versão não encontrada."));
        Entrega entrega = versao.getEntrega();
        if (!podeCancelarEnvio(versao, ultimaVersao(entrega))) {
            throw new RegraNegocioException("O envio só pode ser cancelado enquanto o orientador não abriu a entrega. "
                    + "Se precisar, peça a exclusão do anexo.");
        }

        if (solicitacaoRepository.existsByVersaoId(versao.getId())) {
            throw new RegraNegocioException("Este envio já teve um pedido de exclusão e não pode mais ser cancelado. "
                    + "Se precisar, peça a exclusão do anexo de novo.");
        }

        String chave = versao.getChaveArquivo();
        boolean arquivoCompartilhado = entregaVersaoRepository.existsByChaveArquivoAndIdNot(chave, versao.getId());
        int numero = versao.getNumero();
        entregaVersaoRepository.delete(versao);
        entregaVersaoRepository.flush();

        Entrega restante = null;
        if (entregaVersaoRepository.countByEntregaId(entrega.getId()) == 0) {
            entregaRepository.delete(entrega);
        } else {
            entrega.setStatus(StatusEntrega.DEVOLVIDA);
            restante = entregaRepository.save(entrega);
        }
        if (!arquivoCompartilhado) {
            excluirArquivoDepoisDeGravar(chave);
        }

        auditoriaService.registrar(estagio.getMatricula().getAluno(), "ENVIO_CANCELADO",
                "Versão " + numero + " da entrega id=" + entrega.getId() + " cancelada pelo aluno", ipOrigem, true);
        log.info("[ENTREGA] Versão {} da entrega {} cancelada por {}", numero, entrega.getId(), emailAluno);
        return montarDetalhe(entrega.getTarefa(), restante);
    }

    /*
       Exclui o anexo de uma versão depois da aprovação do pedido. O nome do arquivo sai, a versão
       fica no histórico com a data e quem aprovou, e o arquivo é apagado sem ninguém abrir. Se a
       versão era a última e a entrega ainda contava com ela a entrega volta para o aluno reenviar
    */
    @Transactional
    public void excluirAnexo(EntregaVersao versao, Usuario quemAprovou) {
        String chave = versao.getChaveArquivo();
        boolean arquivoCompartilhado = entregaVersaoRepository.existsByChaveArquivoAndIdNot(chave, versao.getId());
        versao.setSituacaoArquivo(SituacaoArquivo.EXCLUIDO);
        versao.setNomeArquivo("Anexo excluído");
        versao.setArquivoExcluidoEm(OffsetDateTime.now());
        versao.setExclusaoAprovadaPor(quemAprovou);
        entregaVersaoRepository.save(versao);

        Entrega entrega = versao.getEntrega();
        boolean ultima = ultimaVersao(entrega).getId().equals(versao.getId());
        if (ultima && entrega.getStatus() != StatusEntrega.DEVOLVIDA) {
            entrega.setStatus(StatusEntrega.DEVOLVIDA);
            entregaRepository.save(entrega);
        }
        if (!arquivoCompartilhado) {
            excluirArquivoDepoisDeGravar(chave);
        }
        log.info("[ENTREGA] Anexo da versão {} da entrega {} excluído", versao.getNumero(), entrega.getId());
    }

    // Download de uma versão pelo aluno, só das entregas dele, registrado na auditoria
    @Transactional
    public ArquivoEntrega baixarDoAluno(String emailAluno, Long versaoId, String ipOrigem) {
        EntregaVersao versao = entregaVersaoRepository.findByIdAndEntregaEstagioMatriculaAlunoEmail(versaoId, emailAluno)
                .orElseThrow(() -> new ResourceNotFoundException("Arquivo não encontrado."));
        ArquivoEntrega arquivo = lerArquivo(versao);
        registrarDownload(versao.getEntrega().getEstagio().getMatricula().getAluno(), versao, ipOrigem);
        return arquivo;
    }

    // Download de uma versão pelo orientador, só das entregas dos alunos dele, registrado na auditoria
    @Transactional
    public ArquivoEntrega baixarDoOrientador(String emailOrientador, Long versaoId, String ipOrigem) {
        EntregaVersao versao = entregaVersaoRepository.findByIdAndEntregaEstagioOrientadorEmail(versaoId, emailOrientador)
                .orElseThrow(() -> new ResourceNotFoundException("Arquivo não encontrado."));
        ArquivoEntrega arquivo = lerArquivo(versao);
        registrarDownload(versao.getEntrega().getEstagio().getOrientador(), versao, ipOrigem);
        return arquivo;
    }

    // Registra quem baixou, qual versão e de onde
    private void registrarDownload(Usuario quemBaixou, EntregaVersao versao, String ipOrigem) {
        auditoriaService.registrar(quemBaixou, "ARQUIVO_BAIXADO",
                "Versão " + versao.getNumero() + " (id=" + versao.getId() + ") da entrega id="
                        + versao.getEntrega().getId(), ipOrigem, true);
    }

    // Status da entrega de cada tarefa do estágio, para a trilha do aluno
    @Transactional(readOnly = true)
    public Map<Long, Entrega> entregasPorTarefa(Long estagioId) {
        Map<Long, Entrega> entregaPorIdDaTarefa = new HashMap<>();
        for (Entrega entrega : entregaRepository.findByEstagioId(estagioId)) {
            entregaPorIdDaTarefa.put(entrega.getTarefa().getId(), entrega);
        }
        return entregaPorIdDaTarefa;
    }

    @Transactional(readOnly = true)
    public long contarVersoes(Long entregaId) {
        return entregaVersaoRepository.countByEntregaId(entregaId);
    }

    // Monta a resposta com o histórico completo, sem os critérios para preencher (tela do aluno)
    public EntregaDetalheResponse montarDetalhe(Tarefa tarefa, Entrega entrega) {
        return montarDetalhe(tarefa, entrega, new ArrayList<>());
    }

    /*
       Monta a resposta com o histórico completo, igual para o aluno e para o orientador. O orientador
       recebe também os critérios ativos da tarefa, para preencher o checklist na correção
    */
    public EntregaDetalheResponse montarDetalhe(Tarefa tarefa, Entrega entrega,
                                                List<CriterioChecklistResponse> criteriosParaAvaliar) {
        // Tarefa sem entrega não tem versões. Com entrega, as versões vão da primeira para a última
        List<EntregaVersaoResponse> versoes = new ArrayList<>();
        if (entrega != null && entrega.getId() != null) {
            List<EntregaVersao> gravadas = entregaVersaoRepository.findByEntregaIdOrderByNumeroAsc(entrega.getId());
            boolean algumaEmAnalise = gravadas.stream()
                    .anyMatch(versao -> versao.getSituacaoArquivo() == SituacaoArquivo.EXCLUSAO_EM_ANALISE);
            EntregaVersao ultima = gravadas.isEmpty() ? null : gravadas.get(gravadas.size() - 1);
            for (EntregaVersao versao : gravadas) {
                versoes.add(paraVersaoResponse(versao, podeCancelarEnvio(versao, ultima),
                        versao.arquivoDisponivel() && !algumaEmAnalise));
            }
        }

        StatusEntrega status = entrega == null ? null : entrega.getStatus();
        boolean exclusaoEmAnalise = false;
        for (EntregaVersaoResponse versao : versoes) {
            if (SituacaoArquivo.EXCLUSAO_EM_ANALISE.name().equals(versao.getSituacaoArquivo())) {
                exclusaoEmAnalise = true;
            }
        }
        return EntregaDetalheResponse.builder()
                .entregaId(entrega == null ? null : entrega.getId())
                .tarefaId(tarefa.getId())
                .nomeTarefa(tarefa.getNome())
                .descricaoTarefa(tarefa.getDescricao())
                .nomeAluno(entrega == null ? null : entrega.getEstagio().getMatricula().getAluno().getNome())
                .status(status == null ? StatusEntrega.PENDENTE.name() : status.name())
                .podeEnviar((status == null || podeEnviar(status)) && !exclusaoEmAnalise)
                .exclusaoEmAnalise(exclusaoEmAnalise)
                .versoes(versoes)
                .criterios(criteriosParaAvaliar)
                .build();
    }

    // Sem entrega ou com a última versão devolvida, o aluno pode mandar um arquivo
    static boolean podeEnviar(StatusEntrega status) {
        return status == StatusEntrega.PENDENTE || status == StatusEntrega.DEVOLVIDA;
    }

    // Só a última versão, ainda não aberta pelo orientador e com o anexo disponível
    private static boolean podeCancelarEnvio(EntregaVersao versao, EntregaVersao ultima) {
        return ultima != null && ultima.getId().equals(versao.getId())
                && versao.getEntrega().getStatus() == StatusEntrega.ENVIADA
                && versao.arquivoDisponivel();
    }

    private EntregaVersao ultimaVersao(Entrega entrega) {
        return entregaVersaoRepository.findFirstByEntregaIdOrderByNumeroDesc(entrega.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Entrega não encontrada."));
    }

    // Apaga o arquivo só depois que o banco confirmou a mudança. Uma falha aqui fica no log
    private void excluirArquivoDepoisDeGravar(String chave) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            armazenamentoArquivos.excluir(chave);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    armazenamentoArquivos.excluir(chave);
                } catch (RuntimeException excecao) {
                    log.error("[ARQUIVO] Não foi possível excluir {}: {}", chave, excecao.getMessage(), excecao);
                }
            }
        });
    }

    private EntregaVersaoResponse paraVersaoResponse(EntregaVersao versao, boolean podeCancelarEnvio,
                                                     boolean podeSolicitarExclusao) {
        return EntregaVersaoResponse.builder()
                .id(versao.getId())
                .numero(versao.getNumero())
                .nomeArquivo(versao.getNomeArquivo())
                .tipoArquivo(versao.getTipoArquivo().name())
                .tamanhoBytes(versao.getTamanhoBytes())
                .comentarioAluno(versao.getComentarioAluno())
                .enviadaEm(versao.getEnviadaEm())
                .resultado(versao.getResultado() == null ? null : versao.getResultado().name())
                .feedback(versao.getFeedback())
                .nomeAvaliador(versao.getAvaliador() == null ? null : versao.getAvaliador().getNome())
                .avaliadaEm(versao.getAvaliadaEm())
                .checklist(checklistService.listarRespostasDaVersao(versao.getId()))
                .situacaoArquivo(versao.arquivoDisponivel() ? SituacaoArquivo.DISPONIVEL.name() : versao.getSituacaoArquivo().name())
                .arquivoExcluidoEm(versao.getArquivoExcluidoEm())
                .nomeQuemAprovouExclusao(versao.getExclusaoAprovadaPor() == null ? null : versao.getExclusaoAprovadaPor().getNome())
                .podeCancelarEnvio(podeCancelarEnvio)
                .podeSolicitarExclusao(podeSolicitarExclusao)
                .build();
    }

    private ArquivoEntrega lerArquivo(EntregaVersao versao) {
        if (!versao.arquivoDisponivel()) {
            throw new RegraNegocioException(versao.getSituacaoArquivo() == SituacaoArquivo.EXCLUIDO
                    ? "Este anexo foi excluído a pedido do aluno."
                    : "Este anexo está bloqueado enquanto o pedido de exclusão é analisado.");
        }
        byte[] conteudo = armazenamentoArquivos.ler(versao.getChaveArquivo());
        return new ArquivoEntrega(versao.getNomeArquivo(),
                ValidadorArquivoEntrega.tipoConteudo(versao.getTipoArquivo()), conteudo);
    }

    // Mesma cadeia da trilha, matrícula ativa, depois estágio ATIVO. Sem isso não há entrega
    private Estagio buscarEstagioAtivo(String emailAluno) {
        Matricula matricula = matriculaRepository.findFirstByAlunoEmailAndStatus(emailAluno, StatusMatricula.ATIVA)
                .orElseThrow(() -> new ResourceNotFoundException("Estágio ativo não encontrado."));
        return estagioRepository.findByMatriculaIdAndStatus(matricula.getId(), StatusEstagio.ATIVO)
                .orElseThrow(() -> new ResourceNotFoundException("Estágio ativo não encontrado."));
    }

    // A tarefa precisa ser da trilha do tipo do estágio do aluno. Tarefa de outra trilha vira 404
    private Tarefa buscarTarefaDaTrilha(Long tarefaId, Estagio estagio) {
        Tarefa tarefa = tarefaRepository.findById(tarefaId)
                .orElseThrow(() -> new ResourceNotFoundException("Tarefa não encontrada na sua trilha."));
        if (!tarefa.getTrilha().getTipoEstagio().getId().equals(estagio.getTipoEstagio().getId())) {
            throw new ResourceNotFoundException("Tarefa não encontrada na sua trilha.");
        }
        return tarefa;
    }
}
