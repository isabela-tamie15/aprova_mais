package tcc.ges.aprovamais.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Testa as regras de validação dos formulários direto nos DTOs, sem subir o Spring
class ValidacaoFormulariosTest {

    private static ValidatorFactory fabrica;
    private static Validator validator;

    @BeforeAll
    static void iniciar() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validator = fabrica.getValidator();
    }

    @AfterAll
    static void fechar() {
        fabrica.close();
    }

    // Devolve só os nomes dos campos com erro, pra deixar os asserts mais legíveis
    private static Set<String> camposComErro(Object objeto) {
        return validator.validate(objeto).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private static EstagioCadastroRequest estagioValido() {
        EstagioCadastroRequest request = new EstagioCadastroRequest();
        request.setTipoEstagioId(1L);
        request.setNomeEmpresa("Empresa Teste");
        request.setDataInicio(LocalDate.now());
        return request;
    }

    private static PrimeiroAcessoRequest primeiroAcesso(String senha, String confirmacao) {
        PrimeiroAcessoRequest request = new PrimeiroAcessoRequest();
        request.setSenha(senha);
        request.setConfirmacaoSenha(confirmacao);
        return request;
    }

    @Test
    @DisplayName("Cadastro de estágio com todos os campos certos passa sem erro")
    void deveAceitarEstagioValido() {
        assertTrue(camposComErro(estagioValido()).isEmpty());
    }

    @Test
    @DisplayName("Nome da empresa vazio, curto demais ou longo demais é recusado")
    void deveRecusarNomeDaEmpresaInvalido() {
        for (String nome : new String[]{"   ", "A", "x".repeat(151)}) {
            EstagioCadastroRequest request = estagioValido();
            request.setNomeEmpresa(nome);
            assertTrue(camposComErro(request).contains("nomeEmpresa"), "deveria recusar: '" + nome + "'");
        }
    }

    @Test
    @DisplayName("Data de início mais de 1 ano no futuro é recusada, no passado é aceita")
    void deveLimitarDataDeInicioNoFuturo() {
        EstagioCadastroRequest futura = estagioValido();
        futura.setDataInicio(LocalDate.now().plusYears(1).plusDays(1));
        assertEquals(Set.of("dataInicioDentroDoLimite"), camposComErro(futura));

        EstagioCadastroRequest passada = estagioValido();
        passada.setDataInicio(LocalDate.now().minusYears(1));
        assertTrue(camposComErro(passada).isEmpty());
    }

    @Test
    @DisplayName("Aprovação sem data é aceita, com data longe demais no futuro não")
    void deveValidarDataDaAprovacao() {
        AprovacaoRequest semData = new AprovacaoRequest();
        assertTrue(camposComErro(semData).isEmpty());

        AprovacaoRequest futura = new AprovacaoRequest();
        futura.setDataInicio(LocalDate.now().plusYears(2));
        assertEquals(Set.of("dataInicioDentroDoLimite"), camposComErro(futura));
    }

    @Test
    @DisplayName("Senha precisa de 8 a 72 caracteres, com pelo menos uma letra e um número")
    void deveExigirSenhaForte() {
        assertTrue(camposComErro(primeiroAcesso("senha123", "senha123")).isEmpty());
        assertTrue(camposComErro(primeiroAcesso("ação2026", "ação2026")).isEmpty());

        assertTrue(camposComErro(primeiroAcesso("abcdefgh", "abcdefgh")).contains("senha"));
        assertTrue(camposComErro(primeiroAcesso("12345678", "12345678")).contains("senha"));
        assertTrue(camposComErro(primeiroAcesso("abc123", "abc123")).contains("senha"));
        String longa = "a1".repeat(37);
        assertTrue(camposComErro(primeiroAcesso(longa, longa)).contains("senha"));
    }

    @Test
    @DisplayName("Senha e confirmação diferentes são recusadas")
    void deveRecusarConfirmacaoDiferente() {
        assertEquals(Set.of("senhasConferem"), camposComErro(primeiroAcesso("senha123", "senha124")));
    }

    @Test
    @DisplayName("E-mail do convite precisa ter formato válido")
    void deveValidarEmailDoConvite() {
        ConviteRequest request = new ConviteRequest();
        request.setEmailDestino("nao-e-email");
        request.setPerfil(tcc.ges.aprovamais.entity.enums.PerfilDestino.ALUNO);
        assertEquals(Set.of("emailDestino"), camposComErro(request));
    }
}
