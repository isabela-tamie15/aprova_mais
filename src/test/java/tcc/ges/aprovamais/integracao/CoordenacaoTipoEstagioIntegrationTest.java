package tcc.ges.aprovamais.integracao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tcc.ges.aprovamais.entity.Curso;
import tcc.ges.aprovamais.entity.Estagio;
import tcc.ges.aprovamais.entity.TipoEstagio;
import tcc.ges.aprovamais.entity.Turma;
import tcc.ges.aprovamais.entity.enums.Modalidade;
import tcc.ges.aprovamais.entity.enums.Periodo;
import tcc.ges.aprovamais.entity.enums.StatusEstagio;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Testa o cadastro de tipos de estágio pela API (RF14, RN30, RN31)
class CoordenacaoTipoEstagioIntegrationTest extends CoordenacaoTestBase {

    private Curso curso;

    @BeforeEach
    void setUp() {
        curso = criarCurso("Engenharia de Software", "1105", coordenador);
    }

    private String corpo(String nome, String carga, String jornada) {
        return """
                {
                  "cursoId": %d,
                  "nome": "%s",
                  "cargaHorariaNecessaria": %s,
                  "jornadaSemanal": %s
                }
                """.formatted(curso.getId(), nome, carga, jornada);
    }

    @Test
    @DisplayName("Tipo cadastrado pelo coordenador aparece para o aluno do curso com carga e jornada (CA01 do RF14)")
    void deveCadastrarTipoVisivelParaOAluno() throws Exception {
        Turma turma = new Turma();
        turma.setCurso(curso);
        turma.setSemestre("2026/2");
        turma.setIdentificador("8B");
        turma.setPeriodo(Periodo.NOTURNO);
        turma.setModalidade(Modalidade.PRESENCIAL);
        entityManager.persist(turma);
        criarMatricula(criarAluno(EMAIL_ALUNO, "RGM-001"), turma);

        mockMvc.perform(post("/api/v1/coordenador/tipos-estagio")
                        .with(user(EMAIL_COORDENADOR).roles("COORDENADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("Estagiário", "270", "30")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/aluno/estagio/tipos").with(user(EMAIL_ALUNO).roles("ALUNO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("Estagiário"))
                .andExpect(jsonPath("$[0].cargaHorariaNecessaria").value(270))
                .andExpect(jsonPath("$[0].jornadaSemanal").value(30));
    }

    @Test
    @DisplayName("Mudar o tipo não mexe na carga e na jornada de um estágio já cadastrado (CA02 do RF14)")
    void naoDeveAlterarEstagioJaCadastrado() throws Exception {
        Turma turma = criarTurma();
        TipoEstagio tipo = criarTipoEstagio(curso);
        Estagio estagio = criarEstagio(
                criarMatricula(criarAluno(EMAIL_ALUNO, "RGM-001"), turma),
                criarOrientador(EMAIL_ORIENTADOR, "ORI-001"), tipo, StatusEstagio.ATIVO);

        mockMvc.perform(put("/api/v1/coordenador/tipos-estagio/{id}", tipo.getId())
                        .with(user(EMAIL_COORDENADOR).roles("COORDENADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("Estagiario", "300", "20")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jornadaSemanal").value(20));

        entityManager.flush();
        entityManager.clear();
        Estagio recarregado = entityManager.find(Estagio.class, estagio.getId());
        assertEquals(0, new BigDecimal("270").compareTo(recarregado.getCargaHorariaNecessaria()));
        assertEquals(0, new BigDecimal("30").compareTo(recarregado.getJornadaSemanal()));
    }

    @Test
    @DisplayName("Jornada acima de 44 h ou carga zero voltam 400 (CA03 do RF14)")
    void deveRecusarValoresForaDoLimite() throws Exception {
        mockMvc.perform(post("/api/v1/coordenador/tipos-estagio")
                        .with(user(EMAIL_COORDENADOR).roles("COORDENADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("CLT", "0", "45")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.cargaHorariaNecessaria").exists())
                .andExpect(jsonPath("$.campos.jornadaSemanal").exists());
    }

    @Test
    @DisplayName("Tipo em curso de outro coordenador volta 404")
    void deveResponder404ParaCursoDeOutroCoordenador() throws Exception {
        mockMvc.perform(post("/api/v1/coordenador/tipos-estagio")
                        .with(user(EMAIL_OUTRO_COORDENADOR).roles("COORDENADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("CLT", "270", "44")))
                .andExpect(status().isNotFound());
    }
}
