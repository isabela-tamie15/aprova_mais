package tcc.ges.aprovamais.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PaginaEstagioController {

    @GetMapping("/estagio")
    public String paginaEstagio() {
        return "estagio";
    }

    @GetMapping("/orientador/validacoes")
    public String paginaValidacoes() {
        return "validacoes";
    }
}