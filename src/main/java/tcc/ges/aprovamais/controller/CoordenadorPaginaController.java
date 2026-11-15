package tcc.ges.aprovamais.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/*
   Páginas de cadastro da coordenação, separadas do PaginaController para as telas da
   coordenação não mexerem no mesmo arquivo das outras rotas. Os dados vêm da API pelo
   JavaScript da página, então aqui só volta o nome do template
*/
@Controller
@PreAuthorize("hasRole('COORDENADOR')")
public class CoordenadorPaginaController {

    // Página de cadastro de cursos
    @GetMapping("/coordenador/cursos")
    public String cursos() {
        return "coordenador/cursos";
    }

    // Página de cadastro de turmas
    @GetMapping("/coordenador/turmas")
    public String turmas() {
        return "coordenador/turmas";
    }

    // Página de cadastro de tipos de estágio
    @GetMapping("/coordenador/tipos-estagio")
    public String tiposEstagio() {
        return "coordenador/tipos-estagio";
    }

    // Página de montagem das trilhas e tarefas
    @GetMapping("/coordenador/trilhas")
    public String trilhas() {
        return "coordenador/trilhas";
    }

    // Endpoint que serve a página da lista de pré-cadastro
    @GetMapping("/coordenador/pre-cadastros")
    public String preCadastros() {
        return "coordenador/pre-cadastros";
    }
}
