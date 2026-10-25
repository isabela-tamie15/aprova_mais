package tcc.ges.aprovamais.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// Esse controller é quem serve as paginas do Administrador. Os dados vêm da API e carregados com o Alpine no fronted
@Controller
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class AdministradorPaginaController {

    // Esse é o endpoint que serve a tela de consulta da auditoria, que também é a página inicial do perfil
    @GetMapping("/admin/auditoria")
    public String auditoria() {
        return "admin/auditoria";
    }
}
