package tcc.ges.aprovamais.dto;

import tcc.ges.aprovamais.entity.Estagio;
import tcc.ges.aprovamais.entity.Turma;

/*
   Filtros do dashboard do coordenador. Cada campo é opcional, e o campo vazio não filtra.
   O orientador é o do estágio, e o semestre é o da turma
*/
public record FiltroPainel(Long cursoId, String semestre, Long turmaId, Long orientadorId) {

    public FiltroPainel {
        semestre = semestre == null || semestre.isBlank() ? null : semestre.trim();
    }

    // Sem nenhum filtro, o painel mostra todos os cursos do coordenador
    public static FiltroPainel semFiltro() {
        return new FiltroPainel(null, null, null, null);
    }

    // Diz se o estágio entra no painel com os filtros escolhidos
    public boolean aceita(Estagio estagio) {
        Turma turma = estagio.getMatricula().getTurma();
        if (cursoId != null && !cursoId.equals(turma.getCurso().getId())) {
            return false;
        }
        if (semestre != null && !semestre.equals(turma.getSemestre())) {
            return false;
        }
        if (turmaId != null && !turmaId.equals(turma.getId())) {
            return false;
        }
        return orientadorId == null
                || (estagio.getOrientador() != null && orientadorId.equals(estagio.getOrientador().getId()));
    }
}
