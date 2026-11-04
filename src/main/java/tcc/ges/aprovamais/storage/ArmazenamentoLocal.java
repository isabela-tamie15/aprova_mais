package tcc.ges.aprovamais.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

// Guarda os arquivos numa pasta do servidor
// No Docker, a pasta é um volume, então os arquivos sobrevivem ao restart
public class ArmazenamentoLocal implements ArmazenamentoArquivos {

    private static final Logger log = LoggerFactory.getLogger(ArmazenamentoLocal.class);

    private final Path pastaBase;

    public ArmazenamentoLocal(Path pastaBase) {
        this.pastaBase = pastaBase.toAbsolutePath().normalize();
    }

    @Override
    public void salvar(String chave, byte[] conteudo, String tipoConteudo) {
        Path destino = resolver(chave);
        try {
            Files.createDirectories(destino.getParent());
            // CREATE_NEW: uma versão gravada nunca é sobrescrita (RN56)
            Files.write(destino, conteudo, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            log.error("[ARQUIVO] Erro ao gravar {}: {}", chave, e.getMessage(), e);
            throw new UncheckedIOException("Não foi possível gravar o arquivo", e);
        }
    }

    @Override
    public byte[] ler(String chave) {
        try {
            return Files.readAllBytes(resolver(chave));
        } catch (NoSuchFileException e) {
            log.warn("[ARQUIVO] Arquivo não encontrado no disco: {}", chave);
            throw new ResourceNotFoundException("Arquivo não encontrado.");
        } catch (IOException e) {
            log.error("[ARQUIVO] Erro ao ler {}: {}", chave, e.getMessage(), e);
            throw new UncheckedIOException("Não foi possível ler o arquivo", e);
        }
    }

    //Proteção contra path traversal
    private Path resolver(String chave) {
        Path caminho = pastaBase.resolve(chave).normalize();
        if (!caminho.startsWith(pastaBase)) {
            throw new IllegalArgumentException("Chave de arquivo inválida");
        }
        return caminho;
    }
}
