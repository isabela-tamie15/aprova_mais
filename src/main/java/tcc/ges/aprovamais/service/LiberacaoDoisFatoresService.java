package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.dto.PedidoLiberacaoDoisFatoresResponse;
import tcc.ges.aprovamais.entity.PedidoLiberacaoDoisFatores;
import tcc.ges.aprovamais.entity.Usuario;
import tcc.ges.aprovamais.entity.enums.PerfilUsuario;
import tcc.ges.aprovamais.entity.enums.StatusPedidoLiberacao;
import tcc.ges.aprovamais.exception.RegraNegocioException;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.PedidoLiberacaoDoisFatoresRepository;
import tcc.ges.aprovamais.repository.UsuarioRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LiberacaoDoisFatoresService {

    private static final Logger log = LoggerFactory.getLogger(LiberacaoDoisFatoresService.class);

    private final PedidoLiberacaoDoisFatoresRepository pedidoRepository;
    private final UsuarioRepository usuarioRepository;
    private final DoisFatoresService doisFatoresService;
    private final AuditoriaService auditoriaService;

    @Transactional
    public void solicitar(String tokenPreAutenticacao, String motivo, String ipOrigem) {
        Usuario usuario = doisFatoresService.buscarPorTokenPreAutenticacao(tokenPreAutenticacao);

        if (!Boolean.TRUE.equals(usuario.getDoisFatoresAtivo())) {
            throw new RegraNegocioException("A verificação em duas etapas desta conta não está ativa");
        }
        if (pedidoRepository.existsByUsuarioIdAndStatus(usuario.getId(), StatusPedidoLiberacao.PENDENTE)) {
            throw new RegraNegocioException("Você já tem um pedido em análise. Aguarde a resposta");
        }

        PedidoLiberacaoDoisFatores pedido = new PedidoLiberacaoDoisFatores();
        pedido.setUsuario(usuario);
        pedido.setMotivo(motivo == null || motivo.isBlank() ? null : motivo.trim());
        pedido.setCriadoEm(OffsetDateTime.now(ZoneOffset.UTC));
        pedidoRepository.save(pedido);

        auditoriaService.registrar(usuario, "DOIS_FATORES_LIBERACAO_SOLICITADA",
                "Pedido de liberação do 2FA id=" + pedido.getId(), ipOrigem, true);
        log.info("[2FA] Pedido de liberação {} aberto pelo usuário {}", pedido.getId(), usuario.getId());
    }

    @Transactional(readOnly = true)
    public List<PedidoLiberacaoDoisFatoresResponse> listarPendentes(String emailResponsavel) {
        Usuario responsavel = buscarUsuario(emailResponsavel);
        return pedidoRepository.listarPorStatusEPerfis(StatusPedidoLiberacao.PENDENTE, perfisAtendidos(responsavel))
                .stream()
                .map(pedido -> new PedidoLiberacaoDoisFatoresResponse(
                        pedido.getId(),
                        pedido.getUsuario().getNome(),
                        pedido.getUsuario().getEmail(),
                        pedido.getUsuario().getPerfil().name(),
                        pedido.getMotivo(),
                        pedido.getCriadoEm()))
                .toList();
    }

    @Transactional
    public void aprovar(Long pedidoId, String emailResponsavel, String ipOrigem) {
        Usuario responsavel = buscarUsuario(emailResponsavel);
        PedidoLiberacaoDoisFatores pedido = buscarPedidoPendente(pedidoId, responsavel);
        Usuario usuario = pedido.getUsuario();

        usuario.setDoisFatoresAtivo(false);
        usuario.setDoisFatoresSegredo(null);
        usuario.setUltimoPassoTotp(null);
        usuario.encerrarSessoes();
        usuarioRepository.save(usuario);

        decidir(pedido, responsavel, StatusPedidoLiberacao.APROVADO, null);
        auditoriaService.registrar(responsavel, "DOIS_FATORES_LIBERACAO_APROVADA",
                "Pedido id=" + pedido.getId() + ". 2FA do usuário id=" + usuario.getId() + " desativado", ipOrigem, true);
        log.info("[2FA] Pedido de liberação {} aprovado pelo usuário {}", pedido.getId(), responsavel.getId());
    }

    @Transactional
    public void recusar(Long pedidoId, String justificativa, String emailResponsavel, String ipOrigem) {
        Usuario responsavel = buscarUsuario(emailResponsavel);
        PedidoLiberacaoDoisFatores pedido = buscarPedidoPendente(pedidoId, responsavel);

        decidir(pedido, responsavel, StatusPedidoLiberacao.RECUSADO, justificativa.trim());
        auditoriaService.registrar(responsavel, "DOIS_FATORES_LIBERACAO_RECUSADA",
                "Pedido id=" + pedido.getId() + ". Justificativa: " + justificativa.trim(), ipOrigem, true);
    }

    private Set<PerfilUsuario> perfisAtendidos(Usuario responsavel) {
        return switch (responsavel.getPerfil()) {
            case ADMINISTRADOR -> EnumSet.complementOf(EnumSet.of(PerfilUsuario.ADMINISTRADOR));
            case COORDENADOR -> EnumSet.of(PerfilUsuario.ADMINISTRADOR);
            default -> EnumSet.noneOf(PerfilUsuario.class);
        };
    }

    private PedidoLiberacaoDoisFatores buscarPedidoPendente(Long pedidoId, Usuario responsavel) {
        PedidoLiberacaoDoisFatores pedido = pedidoRepository.findById(pedidoId)
                .filter(p -> perfisAtendidos(responsavel).contains(p.getUsuario().getPerfil()))
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado"));
        if (pedido.getStatus() != StatusPedidoLiberacao.PENDENTE) {
            throw new RegraNegocioException("Este pedido já foi analisado");
        }
        if (pedido.getUsuario().getId().equals(responsavel.getId())) {
            throw new RegraNegocioException("Você não pode analisar o seu próprio pedido");
        }
        return pedido;
    }

    private void decidir(PedidoLiberacaoDoisFatores pedido, Usuario responsavel,
                         StatusPedidoLiberacao status, String justificativa) {
        pedido.setStatus(status);
        pedido.setDecididoPor(responsavel);
        pedido.setDecididoEm(OffsetDateTime.now(ZoneOffset.UTC));
        pedido.setJustificativa(justificativa);
        pedidoRepository.save(pedido);
    }

    private Usuario buscarUsuario(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
    }
}
