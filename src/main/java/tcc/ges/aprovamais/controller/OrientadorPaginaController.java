package tcc.ges.aprovamais.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/*
   Páginas do painel de correção do orientador
   Os dados vêm da API /api/v1/orientador/entregas, carregados pelo script de cada página
*/
@Controller
@PreAuthorize("hasRole('ORIENTADOR')")
public class OrientadorPaginaController {

    @GetMapping("/orientador/correcoes")
    public String painelCorrecao() {
        return "orientador/correcoes";
    }

    @GetMapping("/orientador/correcoes/{entregaId}")
    public String avaliacaoEntrega(@PathVariable Long entregaId, Model model) {
        model.addAttribute("entregaId", entregaId);
        return "orientador/correcao";
    }

    // Histórico das correções feitas pelo orientador
    @GetMapping("/orientador/historico")
    public String historicoCorrecoes() {
        return "orientador/historico";
    }

    // Os dados vem de /api/v1/orientador/estagios/{id}/visao
    @GetMapping("/orientador/acompanhamento/{estagioId}")
    public String acompanhamentoAluno(@PathVariable Long estagioId, Model model) {
        model.addAttribute("estagioId", estagioId);
        return "orientador/acompanhamento";
    }
}
