package tcc.ges.aprovamais.integracao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tcc.ges.aprovamais.entity.*;
import tcc.ges.aprovamais.entity.enums.StatusEstagio;
import tcc.ges.aprovamais.repository.EstagioRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;


//Teste de persistência do EstagioRepository contra o banco H2
class EstagioRepositoryIntegrationTest extends IntegracaoTestBase {

    @Autowired
    private EstagioRepository estagioRepository;


    @Test
    @DisplayName("findByIdAndOrientadorEmail só encontra o estágio para o orientador responsável")
    void deveEncontrarEstagioApenasParaOrientadorResponsavel() {


        // aqui tem dois orientadores, o estagio vinculado só ao primeiro
        Turma turma = criarTurma();

        TipoEstagio tipo = criarTipoEstagio(turma.getCurso());

        Orientador responsavel = criarOrientador(EMAIL_ORIENTADOR, "ORI-001");

        criarOrientador("outro.orientador@teste.com", "ORI-002");

        Matricula matricula = criarMatricula(criarAluno(EMAIL_ALUNO, "RGM-001"), turma);

        Estagio estagio = criarEstagio(matricula, responsavel, tipo, StatusEstagio.PENDENTE);
        entityManager.clear();


        // act
        Optional<Estagio> doResponsavel =
                estagioRepository.findByIdAndOrientadorEmail(estagio.getId(), EMAIL_ORIENTADOR);
        Optional<Estagio> deOutro =
                estagioRepository.findByIdAndOrientadorEmail(estagio.getId(), "outro.orientador@teste.com");

        // assert
        assertTrue(doResponsavel.isPresent());
        assertEquals("Empresa Teste", doResponsavel.get().getNomeEmpresa());

        assertEquals(StatusEstagio.PENDENTE, doResponsavel.get().getStatus());
        
        assertNotNull(doResponsavel.get().getCriadoEm(), "auditoria do JPA deve preencher criadoEm");
        assertTrue(deOutro.isEmpty());
    }
}