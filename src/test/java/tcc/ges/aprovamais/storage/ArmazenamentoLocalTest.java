package tcc.ges.aprovamais.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArmazenamentoLocalTest {

    @TempDir
    Path pasta;

    @Test
    @DisplayName("Grava e lê o arquivo pela chave, criando as pastas")
    void deveGravarELer() {
        ArmazenamentoLocal armazenamento = new ArmazenamentoLocal(pasta);
        byte[] conteudo = "%PDF-1.4".getBytes(StandardCharsets.US_ASCII);

        armazenamento.salvar("entregas/estagio-1/tarefa-2/v1-abc.pdf", conteudo, "application/pdf");

        assertArrayEquals(conteudo, armazenamento.ler("entregas/estagio-1/tarefa-2/v1-abc.pdf"));
    }

    @Test
    @DisplayName("Uma versão gravada nunca é sobrescrita (RN56)")
    void naoDeveSobrescrever() {
        ArmazenamentoLocal armazenamento = new ArmazenamentoLocal(pasta);
        armazenamento.salvar("v1.pdf", new byte[]{1}, "application/pdf");

        assertThrows(UncheckedIOException.class, () -> armazenamento.salvar("v1.pdf", new byte[]{2}, "application/pdf"));
        assertArrayEquals(new byte[]{1}, armazenamento.ler("v1.pdf"));
    }

    @Test
    @DisplayName("Chave com ../ não sai da pasta base, e arquivo inexistente vira 404")
    void deveProtegerAPastaBase() {
        ArmazenamentoLocal armazenamento = new ArmazenamentoLocal(pasta);

        assertThrows(IllegalArgumentException.class,
                () -> armazenamento.salvar("../fora.pdf", new byte[]{1}, "application/pdf"));
        assertThrows(ResourceNotFoundException.class, () -> armazenamento.ler("nao-existe.pdf"));
    }
}
