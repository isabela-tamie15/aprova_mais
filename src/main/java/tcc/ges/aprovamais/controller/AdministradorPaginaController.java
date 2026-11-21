package tcc.ges.aprovamais.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// Páginas do administrador. Os dados vêm da API e são carregados com o Alpine
@Controller
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class AdministradorPaginaController {

    // Tela de consulta da auditoria, que também é a página inicial do perfil
    @GetMapping("/admin/auditoria")
    public String auditoria() {
        return "admin/auditoria";
    }

    // Tela dos parâmetros do sistema
    @GetMapping("/admin/configuracoes")
    public String configuracoes() {
        return "admin/configuracoes";
    }

    // Tela das correções excepcionais, como desativar o 2FA de um usuário
    @GetMapping("/admin/correcoes")
    public String correcoes() {
        return "admin/correcoes";
    }

    // Tela em que o administrador convida os coordenadores
    @GetMapping("/admin/convites")
    public String convites() {
        return "admin/convites";
    }
}
