package tcc.ges.aprovamais.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tcc.ges.aprovamais.entity.enums.TipoArquivo;
import tcc.ges.aprovamais.exception.DadoInvalidoException;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidadorArquivoEntregaTest {

    private static final byte[] PDF = "%PDF-1.7 conteúdo".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ZIP = {'P', 'K', 3, 4, 20, 0};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0};
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};

    @Test
    @DisplayName("Aceita os formatos da RN56, pela extensão e pelo começo do conteúdo")
    void deveAceitarOsFormatosPermitidos() {
        assertEquals(TipoArquivo.PDF, ValidadorArquivoEntrega.validar("relatorio.pdf", PDF));
        assertEquals(TipoArquivo.DOCX, ValidadorArquivoEntrega.validar("Relatorio Final.DOCX", ZIP));
        assertEquals(TipoArquivo.XLSX, ValidadorArquivoEntrega.validar("horas.xlsx", ZIP));
        assertEquals(TipoArquivo.PPTX, ValidadorArquivoEntrega.validar("apresentacao.pptx", ZIP));
        assertEquals(TipoArquivo.PNG, ValidadorArquivoEntrega.validar("assinatura.png", PNG));
        assertEquals(TipoArquivo.JPEG, ValidadorArquivoEntrega.validar("foto.jpeg", JPG));
    }

    @Test
    @DisplayName("Arquivo .exe é recusado com a lista de formatos aceitos (CA02 do RF33)")
    void deveRecusarFormatoNaoAceito() {
        DadoInvalidoException erro = assertThrows(DadoInvalidoException.class,
                () -> ValidadorArquivoEntrega.validar("programa.exe", new byte[]{'M', 'Z'}));
        assertTrue(erro.getMessage().contains("PDF, DOCX, XLSX, PPTX, JPG, JPEG ou PNG"));
    }

    @Test
    @DisplayName("Arquivo sem extensão é recusado")
    void deveRecusarArquivoSemExtensao() {
        assertThrows(DadoInvalidoException.class, () -> ValidadorArquivoEntrega.validar("relatorio", PDF));
    }

    @Test
    @DisplayName("Executável renomeado para .pdf é recusado pelo conteúdo")
    void deveRecusarConteudoDiferenteDaExtensao() {
        DadoInvalidoException erro = assertThrows(DadoInvalidoException.class,
                () -> ValidadorArquivoEntrega.validar("falso.pdf", new byte[]{'M', 'Z', 0, 0, 0}));
        assertTrue(erro.getMessage().contains("PDF"));
    }

    @Test
    @DisplayName("Arquivo vazio ou acima de 10 MB é recusado")
    void deveRecusarArquivoVazioOuGrande() {
        assertThrows(DadoInvalidoException.class, () -> ValidadorArquivoEntrega.validar("vazio.pdf", new byte[0]));

        byte[] grande = new byte[(int) ValidadorArquivoEntrega.TAMANHO_MAXIMO_BYTES + 1];
        System.arraycopy(PDF, 0, grande, 0, PDF.length);
        assertThrows(DadoInvalidoException.class, () -> ValidadorArquivoEntrega.validar("grande.pdf", grande));
    }

    @Test
    @DisplayName("O nome gravado fica sem pastas, sem caracteres de controle e com até 200 caracteres")
    void deveLimparONomeDoArquivo() {
        assertEquals("relatorio.pdf", ValidadorArquivoEntrega.limparNome("C:\\Users\\aluno\\relatorio.pdf"));
        assertEquals("passwd.pdf", ValidadorArquivoEntrega.limparNome("../../etc/passwd.pdf"));
        assertEquals("arquivo", ValidadorArquivoEntrega.limparNome("   "));

        String longo = ValidadorArquivoEntrega.limparNome("a".repeat(300) + ".pdf");
        assertEquals(200, longo.length());
        assertTrue(longo.endsWith(".pdf"));
    }
}
