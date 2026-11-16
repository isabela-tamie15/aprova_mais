package tcc.ges.aprovamais.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tcc.ges.aprovamais.entity.Administrador;
import tcc.ges.aprovamais.entity.ConfiguracaoSistema;
import tcc.ges.aprovamais.entity.enums.ParametroSistema;
import tcc.ges.aprovamais.exception.DadoInvalidoException;
import tcc.ges.aprovamais.exception.RegraNegocioException;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.ConfiguracaoSistemaRepository;
import tcc.ges.aprovamais.repository.UsuarioRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConfiguracaoServiceTest {

    private static final String EMAIL_ADMIN = "admin@teste.com";
    private static final String IP = "127.0.0.1";

    @Mock
    private ConfiguracaoSistemaRepository configuracaoSistemaRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private AuditoriaService auditoriaService;
    @InjectMocks
    private ConfiguracaoService configuracaoService;

    @AfterEach
    void restaurar() {
        ParametrosVigentes.restaurarPadroes();
    }

    @Test
    @DisplayName("O prazo de arrependimento alterado de 7 para 10 dias passa a valer e fica na auditoria")
    void deveAlterarParametro() {
        when(usuarioRepository.findByEmail(EMAIL_ADMIN)).thenReturn(Optional.of(new Administrador()));
        when(configuracaoSistemaRepository.findById(ParametroSistema.PRAZO_ARREPENDIMENTO_DIAS)).thenReturn(Optional.empty());
        when(configuracaoSistemaRepository.save(any(ConfiguracaoSistema.class))).thenAnswer(chamada -> chamada.getArgument(0));

        var resposta = configuracaoService.alterar("PRAZO_ARREPENDIMENTO_DIAS", 10, EMAIL_ADMIN, IP);

        assertEquals(10, resposta.getValor());
        assertEquals(10, ParametrosVigentes.valor(ParametroSistema.PRAZO_ARREPENDIMENTO_DIAS));
        verify(auditoriaService).registrar(any(), eq("CONFIGURACAO_ALTERADA"),
                eq("Prazo de arrependimento do trancamento: de 7 para 10 dias"), eq(IP), eq(true));
    }

    @Test
    @DisplayName("Recusa zero tentativas, abaixo do mínimo")
    void naoDeveAceitarValorAbaixoDoMinimo() {
        assertThrows(DadoInvalidoException.class,
                () -> configuracaoService.alterar("TENTATIVAS_ANTES_BLOQUEIO", 0, EMAIL_ADMIN, IP));
        assertEquals(5, ParametrosVigentes.valor(ParametroSistema.TENTATIVAS_ANTES_BLOQUEIO));
        verify(configuracaoSistemaRepository, never()).save(any());
    }

    @Test
    @DisplayName("O aviso vermelho não pode ter mais dias que o amarelo")
    void naoDeveInverterOsAvisos() {
        assertThrows(RegraNegocioException.class,
                () -> configuracaoService.alterar("DIAS_AVISO_VERMELHO", 30, EMAIL_ADMIN, IP));
    }

    @Test
    @DisplayName("Parâmetro inexistente responde 404")
    void naoDeveAceitarParametroInexistente() {
        assertThrows(ResourceNotFoundException.class,
                () -> configuracaoService.alterar("NAO_EXISTE", 1, EMAIL_ADMIN, IP));
    }

    @Test
    @DisplayName("Na subida, os valores gravados passam a valer")
    void deveCarregarValoresGravados() {
        ConfiguracaoSistema gravada = new ConfiguracaoSistema();
        gravada.setParametro(ParametroSistema.HORAS_VALIDADE_SESSAO);
        gravada.setValor(4);
        when(configuracaoSistemaRepository.findAll()).thenReturn(List.of(gravada));

        configuracaoService.carregarValores();

        assertEquals(4, ParametrosVigentes.valor(ParametroSistema.HORAS_VALIDADE_SESSAO));
        assertEquals(24, ParametrosVigentes.valor(ParametroSistema.HORAS_VALIDADE_CONVITE));
        verify(auditoriaService, never()).registrar(any(), anyString(), anyString(), anyString(), eq(true));
    }
}
