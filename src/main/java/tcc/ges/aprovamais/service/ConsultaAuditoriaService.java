package tcc.ges.aprovamais.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.dto.FiltroAuditoriaRequest;
import tcc.ges.aprovamais.dto.PaginaAuditoriaResponse;
import tcc.ges.aprovamais.dto.RegistroAuditoriaResponse;
import tcc.ges.aprovamais.entity.LogAuditoria;
import tcc.ges.aprovamais.repository.LogAuditoriaRepository;
import tcc.ges.aprovamais.repository.UsuarioRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/*
   Essa service é quem faz a consulta da auditoria pelo Adm
   Ela vai ficar separada da AuditoriaService porque aquela só grava de forma assincrona, enquanto essa aqui só vai ler
*/
@Service
@RequiredArgsConstructor
public class ConsultaAuditoriaService {

    public static final String ACAO_CONSULTA = "AUDITORIA_CONSULTADA";

    // O dia do filtro é o dia no horário de Brasília, não o do servidor
    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");

    private final LogAuditoriaRepository logAuditoriaRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;

    /*
       Lista os registros que batem com os filtros, do mais recente para o
       mais antigo, e registra a própria consulta na auditoria
    */
    @Transactional(readOnly = true)
    public PaginaAuditoriaResponse consultar(FiltroAuditoriaRequest filtro, String emailAdministrador, String ip) {
        PageRequest paginacao = PageRequest.of(filtro.getPagina(), filtro.getTamanho(),
                Sort.by(Sort.Order.desc("criadoEm"), Sort.Order.desc("id")));

        Page<LogAuditoria> pagina = logAuditoriaRepository.findAll(montarFiltro(filtro), paginacao);

        usuarioRepository.findByEmail(emailAdministrador).ifPresent(administrador ->
                auditoriaService.registrar(administrador, ACAO_CONSULTA, descreverFiltros(filtro), ip, true));

        return new PaginaAuditoriaResponse(
                pagina.getContent().stream().map(RegistroAuditoriaResponse::de).toList(),
                pagina.getNumber(),
                pagina.getSize(),
                pagina.getTotalElements(),
                pagina.getTotalPages()
        );
    }

    // Ações que já existem na auditoria, para o select da tela
    @Transactional(readOnly = true)
    public List<String> listarAcoes() {
        return logAuditoriaRepository.listarAcoesRegistradas();
    }

    // Monta o where só com os filtros preenchidos
    private Specification<LogAuditoria> montarFiltro(FiltroAuditoriaRequest filtro) {
        return (raiz, consulta, cb) -> {
            List<Predicate> condicoes = new ArrayList<>();


            //Email completo (com @) busca exatamente aquele usuario
            if (temTexto(filtro.getEmail())) {
                String email = filtro.getEmail().trim().toLowerCase(Locale.ROOT);
                if (email.contains("@")) {
                    condicoes.add(cb.equal(cb.lower(raiz.get("emailTentativa")), email));
                } else {
                    condicoes.add(cb.like(cb.lower(raiz.get("emailTentativa")), "%" + email + "%"));
                }
            }
            if (temTexto(filtro.getAcao())) {
                condicoes.add(cb.equal(raiz.get("acao"), filtro.getAcao().trim()));
            }
            if (filtro.getSucesso() != null) {
                condicoes.add(cb.equal(raiz.get("sucesso"), filtro.getSucesso()));
            }
            if (filtro.getInicio() != null) {
                condicoes.add(cb.greaterThanOrEqualTo(raiz.get("criadoEm"), inicioDoDia(filtro.getInicio())));
            }
            if (filtro.getFim() != null) {


                // Menor que o começo do dia seguinte para incluir o dia final inteiro
                condicoes.add(cb.lessThan(raiz.get("criadoEm"), inicioDoDia(filtro.getFim().plusDays(1))));
            }
            return cb.and(condicoes.toArray(new Predicate[0]));
        };
    }

    private OffsetDateTime inicioDoDia(LocalDate data) {
        return data.atStartOfDay(ZONA).toOffsetDateTime();
    }

    // Texto que vai para os detalhes do registro da consulta
    private String descreverFiltros(FiltroAuditoriaRequest filtro) {
        List<String> partes = new ArrayList<>();
        if (temTexto(filtro.getEmail())) partes.add("e-mail \"" + filtro.getEmail().trim() + "\"");
        if (temTexto(filtro.getAcao())) partes.add("ação " + filtro.getAcao().trim());
        if (filtro.getSucesso() != null) partes.add(filtro.getSucesso() ? "só sucessos" : "só falhas");
        if (filtro.getInicio() != null) partes.add("de " + filtro.getInicio());
        if (filtro.getFim() != null) partes.add("até " + filtro.getFim());
        return partes.isEmpty()
                ? "Consulta da auditoria sem filtros"
                : "Consulta da auditoria com filtros: " + String.join(", ", partes);
    }

    private boolean temTexto(String valor) {
        return valor != null && !valor.isBlank();
    }
}
