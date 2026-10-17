package tcc.ges.aprovamais.integracao;

import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.entity.*;
import tcc.ges.aprovamais.entity.enums.*;
import tcc.ges.aprovamais.service.AuditoriaService;

import java.math.BigDecimal;
import java.time.LocalDate;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
abstract class IntegracaoTestBase {

    protected static final String EMAIL_ALUNO = "aluno@teste.com";
    protected static final String EMAIL_ORIENTADOR = "orientador@teste.com";



    @Autowired
    protected EntityManager entityManager;

    @MockitoBean
    protected AuditoriaService auditoriaService;



    protected Turma criarTurma() {
        Curso curso = new Curso();
        curso.setNome("Engenharia de Software");
        curso.setCodigoMec("MEC-TESTE-001");
        entityManager.persist(curso);

        Turma turma = new Turma();
        turma.setCurso(curso);
        turma.setSemestre("2026-2");
        turma.setIdentificador("8B");
        turma.setPeriodo(Periodo.NOTURNO);
        turma.setModalidade(Modalidade.PRESENCIAL);
        entityManager.persist(turma);
        return turma;
    }


    protected TipoEstagio criarTipoEstagio(Curso curso) {
        TipoEstagio tipo = new TipoEstagio();
        tipo.setCurso(curso);
        tipo.setNome("Estagiario");
        tipo.setCargaHorariaNecessaria(new BigDecimal("400"));
        entityManager.persist(tipo);
        return tipo;
    }



    protected Aluno criarAluno(String email, String rgm) {
        Aluno aluno = new Aluno();
        aluno.setNome("Aluno Teste");
        aluno.setEmail(email);
        aluno.setSenhaHash("hash-de-teste");
        aluno.setPerfil(PerfilUsuario.ALUNO);
        aluno.setRgm(rgm);
        entityManager.persist(aluno);
        return aluno;}



    protected Orientador criarOrientador(String email, String matriculaInstitucional) {
        Orientador orientador = new Orientador();
        orientador.setNome("Orientador Teste");
        orientador.setEmail(email);
        orientador.setSenhaHash("hash-de-teste");
        orientador.setPerfil(PerfilUsuario.ORIENTADOR);
        orientador.setMatriculaInstitucional(matriculaInstitucional);
        entityManager.persist(orientador);
        return orientador;

    }



    protected Matricula criarMatricula(Aluno aluno, Turma turma) {
        Matricula matricula = new Matricula();
        matricula.setAluno(aluno);
        matricula.setTurma(turma);
        matricula.setStatus(StatusMatricula.ATIVA);
        entityManager.persist(matricula);
        return matricula;

    }

    protected void vincularOrientadorNaTurma(Orientador orientador, Turma turma) {
        OrientadorTurma vinculo = new OrientadorTurma();
        vinculo.setOrientador(orientador);
        vinculo.setTurma(turma);
        entityManager.persist(vinculo);

    }



    protected Estagio criarEstagio(Matricula matricula, Orientador orientador, TipoEstagio tipo, StatusEstagio status) {
        Estagio estagio = new Estagio();
        estagio.setMatricula(matricula);
        estagio.setOrientador(orientador);
        estagio.setTipoEstagio(tipo);
        estagio.setNomeEmpresa("Empresa Teste");
        estagio.setDataInicio(LocalDate.of(2026, 8, 1));
        estagio.setCargaHorariaNecessaria(tipo.getCargaHorariaNecessaria());
        estagio.setStatus(status);
        entityManager.persist(estagio);
        entityManager.flush();


        return estagio;
    }
}