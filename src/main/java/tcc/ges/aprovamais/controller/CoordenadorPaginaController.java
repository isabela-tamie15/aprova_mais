package tcc.ges.aprovamais.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/*
   Esse controller serve as páginas de cadastro da coordenação. Fica separado do
   PaginaController de propósito: assim as telas novas da coordenação não mexem
   no mesmo arquivo que as outras rotas, e duas pessoas trabalhando ao mesmo
   tempo não brigam no merge. Os dados de cada tela vêm da API, pelo JavaScript
   da página, então aqui só devolve o nome do template
*/
@Controller
@PreAuthorize("hasRole('COORDENADOR')")
public class CoordenadorPaginaController {

    // Esse é o endpoint que serve a página de cadastro de cursos
    @GetMapping("/coordenador/cursos")
    public String cursos() {
        return "coordenador/cursos";
    }

    // Esse é o endpoint que serve a página de cadastro de turmas
    @GetMapping("/coordenador/turmas")
    public String turmas() {
        return "coordenador/turmas";
    }
}
