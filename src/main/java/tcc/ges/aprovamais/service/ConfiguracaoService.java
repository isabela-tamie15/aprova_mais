package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.dto.ConfiguracaoResponse;
import tcc.ges.aprovamais.entity.ConfiguracaoSistema;
import tcc.ges.aprovamais.entity.Usuario;
import tcc.ges.aprovamais.entity.enums.ParametroSistema;
import tcc.ges.aprovamais.exception.DadoInvalidoException;
import tcc.ges.aprovamais.exception.RegraNegocioException;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.ConfiguracaoSistemaRepository;
import tcc.ges.aprovamais.repository.UsuarioRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Parâmetros do sistema que o administrador ajusta, dentro dos limites de cada um
@Service
@RequiredArgsConstructor
public class ConfiguracaoService {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracaoService.class);

    private final ConfiguracaoSistemaRepository configuracaoSistemaRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;

    // Quando a aplicação sobe, os valores gravados pelo administrador passam a valer
    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void carregarValores() {
        for (ConfiguracaoSistema configuracao : configuracaoSistemaRepository.findAll()) {
            ParametrosVigentes.definir(configuracao.getParametro(), configuracao.getValor());
        }
    }

    @Transactional(readOnly = true)
    public List<ConfiguracaoResponse> listar() {
        List<ConfiguracaoResponse> respostas = new ArrayList<>();
        for (ParametroSistema parametro : ParametroSistema.values()) {
            Optional<ConfiguracaoSistema> gravada = configuracaoSistemaRepository.findById(parametro);
            respostas.add(paraResponse(parametro, gravada.orElse(null)));
        }
        return respostas;
    }

    /*
       Grava o novo valor e registra na auditoria o valor antigo e o novo. O aviso vermelho precisa
       vir antes do amarelo, senão o amarelo nunca apareceria
    */
    @Transactional
    public ConfiguracaoResponse alterar(String nomeParametro, int novoValor, String emailAdministrador, String ipOrigem) {
        ParametroSistema parametro = buscarParametro(nomeParametro);

        if (novoValor < parametro.getMinimo() || novoValor > parametro.getMaximo()) {
            throw new DadoInvalidoException(parametro.getDescricao() + " deve ficar entre " + parametro.getMinimo()
                    + " e " + parametro.getMaximo() + " " + parametro.getUnidade());
        }
        if (parametro == ParametroSistema.DIAS_AVISO_VERMELHO
                && novoValor >= ParametrosVigentes.valor(ParametroSistema.DIAS_AVISO_AMARELO)) {
            throw new RegraNegocioException("O aviso vermelho precisa ter menos dias que o aviso amarelo");
        }
        if (parametro == ParametroSistema.DIAS_AVISO_AMARELO
                && novoValor <= ParametrosVigentes.valor(ParametroSistema.DIAS_AVISO_VERMELHO)) {
            throw new RegraNegocioException("O aviso amarelo precisa ter mais dias que o aviso vermelho");
        }

        Usuario administrador = usuarioRepository.findByEmail(emailAdministrador)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        int valorAntigo = ParametrosVigentes.valor(parametro);
        if (valorAntigo == novoValor) {
            throw new RegraNegocioException(parametro.getDescricao() + " já está com esse valor");
        }

        ConfiguracaoSistema configuracao = configuracaoSistemaRepository.findById(parametro)
                .orElseGet(ConfiguracaoSistema::new);
        configuracao.setParametro(parametro);
        configuracao.setValor(novoValor);
        configuracao.setAlteradoPor(administrador);
        configuracao.setAlteradoEm(OffsetDateTime.now(ZoneOffset.UTC));
        ConfiguracaoSistema salva = configuracaoSistemaRepository.save(configuracao);
        ParametrosVigentes.definir(parametro, novoValor);

        auditoriaService.registrar(administrador, "CONFIGURACAO_ALTERADA",
                parametro.getDescricao() + ": de " + valorAntigo + " para " + novoValor + " " + parametro.getUnidade(),
                ipOrigem, true);
        log.info("[CONFIGURAÇÃO] {} alterado de {} para {} por {}", parametro, valorAntigo, novoValor, emailAdministrador);
        return paraResponse(parametro, salva);
    }

    private ParametroSistema buscarParametro(String nome) {
        for (ParametroSistema parametro : ParametroSistema.values()) {
            if (parametro.name().equals(nome)) {
                return parametro;
            }
        }
        throw new ResourceNotFoundException("Parâmetro não encontrado");
    }

    private ConfiguracaoResponse paraResponse(ParametroSistema parametro, ConfiguracaoSistema gravada) {
        return ConfiguracaoResponse.builder()
                .parametro(parametro.name())
                .descricao(parametro.getDescricao())
                .unidade(parametro.getUnidade())
                .valor(ParametrosVigentes.valor(parametro))
                .padrao(parametro.getPadrao())
                .minimo(parametro.getMinimo())
                .maximo(parametro.getMaximo())
                .nomeAlteradoPor(gravada != null && gravada.getAlteradoPor() != null ? gravada.getAlteradoPor().getNome() : null)
                .alteradoEm(gravada != null ? gravada.getAlteradoEm() : null)
                .build();
    }
}
