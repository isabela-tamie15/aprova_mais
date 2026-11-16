package tcc.ges.aprovamais.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tcc.ges.aprovamais.dto.CriterioChecklistRequest;
import tcc.ges.aprovamais.dto.RespostaCriterioRequest;
import tcc.ges.aprovamais.entity.*;
import tcc.ges.aprovamais.entity.enums.ResultadoCriterio;
import tcc.ges.aprovamais.exception.DadoInvalidoException;
import tcc.ges.aprovamais.exception.RegraNegocioException;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.CriterioChecklistRepository;
import tcc.ges.aprovamais.repository.RespostaCriterioChecklistRepository;
import tcc.ges.aprovamais.repository.TarefaRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Regras do checklist de critérios (F6), testadas sem banco, com os repositórios simulados pelo Mockito
@ExtendWith(MockitoExtension.class)
class ChecklistServiceTest {

    private static final String EMAIL_COORDENADOR = "coordenador@teste.com";
    private static final String IP = "127.0.0.1";

    @Mock
    private CriterioChecklistRepository criterioChecklistRepository;
    @Mock
    private RespostaCriterioChecklistRepository respostaCriterioChecklistRepository;
    @Mock
    private TarefaRepository tarefaRepository;
    @Mock
    private AuditoriaService auditoriaService;
    @InjectMocks
    private ChecklistService checklistService;

    @Captor
    private ArgumentCaptor<List<RespostaCriterioChecklist>> respostasGravadas;

    private Coordenador coordenador;
    private Tarefa relatorioFinal;
    private CriterioChecklist assinatura;
    private CriterioChecklist atividades;

    @BeforeEach
    void setUp() {
        coordenador = new Coordenador();
        coordenador.setEmail(EMAIL_COORDENADOR);

        Curso curso = new Curso();
        curso.setCoordenador(coordenador);
        TipoEstagio tipo = new TipoEstagio();
        tipo.setCurso(curso);
        Trilha trilha = new Trilha();
        trilha.setTipoEstagio(tipo);

        relatorioFinal = new Tarefa();
        relatorioFinal.setId(20L);
        relatorioFinal.setNome("Relatório Final");
        relatorioFinal.setTrilha(trilha);

        assinatura = criterio(31L, "Assinatura do supervisor", 1);
        atividades = criterio(32L, "Descrição das atividades", 2);
    }

    // ---------- cadastro dos critérios pelo coordenador ----------

    @Test
    @DisplayName("Critério com a mesma ordem de outro critério ativo da tarefa é recusado")
    void naoDeveAdicionarCriterioComOrdemRepetida() {
        // arrange
        when(tarefaRepository.findByIdAndTrilhaTipoEstagioCursoCoordenadorEmail(20L, EMAIL_COORDENADOR))
                .thenReturn(Optional.of(relatorioFinal));
        when(criterioChecklistRepository.existsByTarefaIdAndOrdemAndAtivoTrue(20L, 1)).thenReturn(true);

        // act + assert
        RegraNegocioException excecao = assertThrows(RegraNegocioException.class,
                () -> checklistService.adicionar(20L, EMAIL_COORDENADOR, request("Clareza", 1), IP));
        assertEquals("Já existe um critério na posição 1 desta tarefa", excecao.getMessage());
        verify(criterioChecklistRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tarefa de outro coordenador recebe 404 e nada é gravado")
    void naoDeveAdicionarCriterioEmTarefaDeOutroCoordenador() {
        // arrange
        when(tarefaRepository.findByIdAndTrilhaTipoEstagioCursoCoordenadorEmail(20L, EMAIL_COORDENADOR))
                .thenReturn(Optional.empty());

        // act + assert
        assertThrows(ResourceNotFoundException.class,
                () -> checklistService.adicionar(20L, EMAIL_COORDENADOR, request("Clareza", 1), IP));
        verify(criterioChecklistRepository, never()).save(any());
    }

    @Test
    @DisplayName("Critério novo é gravado ativo, sem espaços sobrando, e a ação vai para a auditoria")
    void deveAdicionarCriterio() {
        // arrange
        when(tarefaRepository.findByIdAndTrilhaTipoEstagioCursoCoordenadorEmail(20L, EMAIL_COORDENADOR))
                .thenReturn(Optional.of(relatorioFinal));
        when(criterioChecklistRepository.existsByTarefaIdAndOrdemAndAtivoTrue(20L, 1)).thenReturn(false);
        when(criterioChecklistRepository.save(any(CriterioChecklist.class))).thenAnswer(chamada -> {
            CriterioChecklist salvo = chamada.getArgument(0);
            salvo.setId(40L);
            return salvo;
        });
        when(criterioChecklistRepository.findByTarefaIdAndAtivoTrueOrderByOrdem(20L)).thenReturn(new ArrayList<>());

        // act
        checklistService.adicionar(20L, EMAIL_COORDENADOR, request("  Clareza  ", 1), IP);

        // assert
        ArgumentCaptor<CriterioChecklist> criterioGravado = ArgumentCaptor.forClass(CriterioChecklist.class);
        verify(criterioChecklistRepository).save(criterioGravado.capture());
        assertEquals("Clareza", criterioGravado.getValue().getDescricao());
        assertTrue(criterioGravado.getValue().getAtivo());
        verify(auditoriaService).registrar(eq(coordenador), eq("CRITERIO_ADICIONADO"), anyString(), eq(IP), eq(true));
    }

    @Test
    @DisplayName("Critério já usado em uma correção é desativado, e não apagado")
    void deveDesativarCriterioJaUsado() {
        // arrange
        when(criterioChecklistRepository.findByIdAndAtivoTrueAndTarefaTrilhaTipoEstagioCursoCoordenadorEmail(
                31L, EMAIL_COORDENADOR)).thenReturn(Optional.of(assinatura));
        when(respostaCriterioChecklistRepository.existsByCriterioId(31L)).thenReturn(true);

        // act
        checklistService.remover(31L, EMAIL_COORDENADOR, IP);

        // assert
        assertFalse(assinatura.getAtivo());
        verify(criterioChecklistRepository).save(assinatura);
        verify(criterioChecklistRepository, never()).delete(any());
        verify(auditoriaService).registrar(eq(coordenador), eq("CRITERIO_DESATIVADO"), anyString(), eq(IP), eq(true));
    }

    @Test
    @DisplayName("Critério que nunca foi usado é apagado")
    void deveApagarCriterioNuncaUsado() {
        // arrange
        when(criterioChecklistRepository.findByIdAndAtivoTrueAndTarefaTrilhaTipoEstagioCursoCoordenadorEmail(
                31L, EMAIL_COORDENADOR)).thenReturn(Optional.of(assinatura));
        when(respostaCriterioChecklistRepository.existsByCriterioId(31L)).thenReturn(false);

        // act
        checklistService.remover(31L, EMAIL_COORDENADOR, IP);

        // assert
        verify(criterioChecklistRepository).delete(assinatura);
        verify(auditoriaService).registrar(eq(coordenador), eq("CRITERIO_REMOVIDO"), anyString(), eq(IP), eq(true));
    }

    // ---------- respostas do orientador na correção ----------

    @Test
    @DisplayName("Com algum critério insuficiente, a entrega não pode ser aprovada")
    void naoDeveAprovarComCriterioInsuficiente() {
        // arrange
        when(criterioChecklistRepository.findByTarefaIdAndAtivoTrueOrderByOrdem(20L))
                .thenReturn(List.of(assinatura, atividades));
        List<RespostaCriterioRequest> respostas = List.of(
                resposta(31L, ResultadoCriterio.INSUFICIENTE, "Faltou a assinatura"),
                resposta(32L, ResultadoCriterio.SUFICIENTE, null));

        // act + assert
        RegraNegocioException excecao = assertThrows(RegraNegocioException.class,
                () -> checklistService.gravarRespostas(new EntregaVersao(), relatorioFinal, respostas, true));
        assertEquals("Há critério insuficiente. Devolva a entrega para o aluno corrigir.", excecao.getMessage());
        verify(respostaCriterioChecklistRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("Critério sem resposta é recusado, mesmo na devolução")
    void naoDeveGravarComCriterioSemResposta() {
        // arrange
        when(criterioChecklistRepository.findByTarefaIdAndAtivoTrueOrderByOrdem(20L))
                .thenReturn(List.of(assinatura, atividades));
        List<RespostaCriterioRequest> respostas = List.of(resposta(31L, ResultadoCriterio.SUFICIENTE, null));

        // act + assert
        assertThrows(DadoInvalidoException.class,
                () -> checklistService.gravarRespostas(new EntregaVersao(), relatorioFinal, respostas, false));
        verify(respostaCriterioChecklistRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("Tarefa sem critérios é corrigida sem checklist")
    void naoDeveGravarNadaEmTarefaSemCriterios() {
        // arrange
        when(criterioChecklistRepository.findByTarefaIdAndAtivoTrueOrderByOrdem(20L)).thenReturn(new ArrayList<>());

        // act
        checklistService.gravarRespostas(new EntregaVersao(), relatorioFinal, null, true);

        // assert
        verify(respostaCriterioChecklistRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("Na devolução, cada resposta guarda a cópia da descrição do critério e a observação")
    void deveGravarAsRespostasComACopiaDoCriterio() {
        // arrange
        when(criterioChecklistRepository.findByTarefaIdAndAtivoTrueOrderByOrdem(20L))
                .thenReturn(List.of(assinatura, atividades));
        EntregaVersao versao = new EntregaVersao();
        List<RespostaCriterioRequest> respostas = List.of(
                resposta(32L, ResultadoCriterio.SUFICIENTE, "   "),
                resposta(31L, ResultadoCriterio.INSUFICIENTE, "  Faltou a assinatura  "));

        // act
        checklistService.gravarRespostas(versao, relatorioFinal, respostas, false);

        // assert: as respostas ficam na ordem do checklist, e não na ordem em que chegaram
        verify(respostaCriterioChecklistRepository).saveAll(respostasGravadas.capture());
        List<RespostaCriterioChecklist> gravadas = respostasGravadas.getValue();
        assertEquals(2, gravadas.size());
        assertEquals("Assinatura do supervisor", gravadas.get(0).getDescricaoCriterio());
        assertEquals(ResultadoCriterio.INSUFICIENTE, gravadas.get(0).getResultado());
        assertEquals("Faltou a assinatura", gravadas.get(0).getObservacao());
        assertSame(versao, gravadas.get(0).getVersao());
        assertNull(gravadas.get(1).getObservacao());
    }

    // auxiliares

    private CriterioChecklist criterio(Long id, String descricao, int ordem) {
        CriterioChecklist criterio = new CriterioChecklist();
        criterio.setId(id);
        criterio.setTarefa(relatorioFinal);
        criterio.setDescricao(descricao);
        criterio.setOrdem(ordem);
        criterio.setAtivo(true);
        return criterio;
    }

    private CriterioChecklistRequest request(String descricao, int ordem) {
        CriterioChecklistRequest request = new CriterioChecklistRequest();
        request.setDescricao(descricao);
        request.setOrdem(ordem);
        return request;
    }

    private RespostaCriterioRequest resposta(Long criterioId, ResultadoCriterio resultado, String observacao) {
        RespostaCriterioRequest resposta = new RespostaCriterioRequest();
        resposta.setCriterioId(criterioId);
        resposta.setResultado(resultado);
        resposta.setObservacao(observacao);
        return resposta;
    }
}
