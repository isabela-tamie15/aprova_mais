package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.dto.EntregaDetalheResponse;
import tcc.ges.aprovamais.dto.EntregaVersaoResponse;
import tcc.ges.aprovamais.entity.*;
import tcc.ges.aprovamais.entity.enums.StatusEntrega;
import tcc.ges.aprovamais.entity.enums.StatusEstagio;
import tcc.ges.aprovamais.entity.enums.StatusMatricula;
import tcc.ges.aprovamais.entity.enums.TipoArquivo;
import tcc.ges.aprovamais.exception.DadoInvalidoException;
import tcc.ges.aprovamais.exception.RegraNegocioException;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.*;
import tcc.ges.aprovamais.storage.ArmazenamentoArquivos;

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
    private final ArmazenamentoArquivos armazenamentoArquivos;
    private final AuditoriaService auditoriaService;

    // Arquivo pronto para o download, vem com nome original, Content-Type e conteúdo
    public record ArquivoEntrega(String nome, String tipoConteudo, byte[] conteudo) {
    }


    // O aluno envia um arquivo para uma tarefa da trilha. Cada envio vira uma versão nova, com número igual ao maior anterior mais 1, e a entrega volta para ENVIADA, aparecendo no painel do orientador
    @Transactional
    public EntregaDetalheResponse enviar(String emailAluno, Long tarefaId, String nomeOriginal,
                                         byte[] conteudo, String comentario, String ipOrigem) {
        Estagio estagio = buscarEstagioAtivo(emailAluno);
        Tarefa tarefa = buscarTarefaDaTrilha(tarefaId, estagio);

        String comentarioLimpo = comentario == null || comentario.isBlank() ? null : comentario.trim();
        if (comentarioLimpo != null && comentarioLimpo.length() > TAMANHO_MAXIMO_COMENTARIO) {
            throw new DadoInvalidoException("O comentário deve ter no máximo 1000 caracteres.");
        }

        // O aluno já enviou essa tarefa antes? Se sim, usamos a mesma entrega; se não, criamos uma nova
        Optional<Entrega> entregaExistente = entregaRepository.findByEstagioIdAndTarefaId(estagio.getId(), tarefa.getId());
        Entrega entrega;
        if (entregaExistente.isPresent()) {
            entrega = entregaExistente.get();
        } else {
            entrega = new Entrega();
            entrega.setEstagio(estagio);
            entrega.setTarefa(tarefa);
        }
        // CA03: com a entrega em análise ou já aprovada, não dá para mandar outra versão
        if (entrega.getStatus() != null && !podeEnviar(entrega.getStatus())) {
            throw new RegraNegocioException(entrega.getStatus() == StatusEntrega.APROVADA
                    ? "Esta tarefa já foi aprovada pelo orientador."
                    : "Esta tarefa já tem uma entrega aguardando a análise do orientador.");
        }

        TipoArquivo tipoArquivo = ValidadorArquivoEntrega.validar(nomeOriginal, conteudo);
        String nomeArquivo = ValidadorArquivoEntrega.limparNome(nomeOriginal);

        // Primeira entrega da tarefa é a versão 1; as próximas são a última versão mais 1 (RN55)
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

        /*
           O saveAndFlush manda as linhas para o banco na hora. Se outro envio da mesma tarefa
           chegou ao mesmo tempo, a restrição única (uk_entrega_estagio_tarefa ou
           uk_versao_entrega_numero) falha aqui e a resposta é 409, antes de o arquivo ser
           gravado. Assim não sobra arquivo sem registro no armazenamento (AUD-22). Se a
           gravação do arquivo falhar, a exceção desfaz a transação e as linhas somem junto
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

    // Download de uma versão pelo aluno, só das entregas dele
    @Transactional(readOnly = true)
    public ArquivoEntrega baixarDoAluno(String emailAluno, Long versaoId) {
        EntregaVersao versao = entregaVersaoRepository.findByIdAndEntregaEstagioMatriculaAlunoEmail(versaoId, emailAluno)
                .orElseThrow(() -> new ResourceNotFoundException("Arquivo não encontrado."));
        return lerArquivo(versao);
    }

    // Download de uma versão pelo orientador, só das entregas dos alunos dele
    @Transactional(readOnly = true)
    public ArquivoEntrega baixarDoOrientador(String emailOrientador, Long versaoId) {
        EntregaVersao versao = entregaVersaoRepository.findByIdAndEntregaEstagioOrientadorEmail(versaoId, emailOrientador)
                .orElseThrow(() -> new ResourceNotFoundException("Arquivo não encontrado."));
        return lerArquivo(versao);
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

    // Monta a resposta com o histórico completo, igual para o aluno e para o orientador
    public EntregaDetalheResponse montarDetalhe(Tarefa tarefa, Entrega entrega) {
        // Tarefa sem entrega ainda não tem versões; com entrega, listamos todas, da primeira para a última
        List<EntregaVersaoResponse> versoes = new ArrayList<>();
        if (entrega != null && entrega.getId() != null) {
            for (EntregaVersao versao : entregaVersaoRepository.findByEntregaIdOrderByNumeroAsc(entrega.getId())) {
                versoes.add(paraVersaoResponse(versao));
            }
        }

        StatusEntrega status = entrega == null ? null : entrega.getStatus();
        return EntregaDetalheResponse.builder()
                .entregaId(entrega == null ? null : entrega.getId())
                .tarefaId(tarefa.getId())
                .nomeTarefa(tarefa.getNome())
                .descricaoTarefa(tarefa.getDescricao())
                .nomeAluno(entrega == null ? null : entrega.getEstagio().getMatricula().getAluno().getNome())
                .status(status == null ? StatusEntrega.PENDENTE.name() : status.name())
                .podeEnviar(status == null || podeEnviar(status))
                .versoes(versoes)
                .build();
    }

    // Sem entrega ou com a última versão devolvida, o aluno pode mandar um arquivo
    static boolean podeEnviar(StatusEntrega status) {
        return status == StatusEntrega.PENDENTE || status == StatusEntrega.DEVOLVIDA;
    }

    private EntregaVersaoResponse paraVersaoResponse(EntregaVersao versao) {
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
                .build();
    }

    private ArquivoEntrega lerArquivo(EntregaVersao versao) {
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

    // A tarefa precisa ser da trilha do tipo do estágio do aluno, vindo de outra trilha vira 404
    private Tarefa buscarTarefaDaTrilha(Long tarefaId, Estagio estagio) {
        Tarefa tarefa = tarefaRepository.findById(tarefaId)
                .orElseThrow(() -> new ResourceNotFoundException("Tarefa não encontrada na sua trilha."));
        if (!tarefa.getTrilha().getTipoEstagio().getId().equals(estagio.getTipoEstagio().getId())) {
            throw new ResourceNotFoundException("Tarefa não encontrada na sua trilha.");
        }
        return tarefa;
    }
}
