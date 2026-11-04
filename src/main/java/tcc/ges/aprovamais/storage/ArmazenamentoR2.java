package tcc.ges.aprovamais.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;

// Guarda os arquivos no Cloudflare R2
public class ArmazenamentoR2 implements ArmazenamentoArquivos {

    private static final Logger log = LoggerFactory.getLogger(ArmazenamentoR2.class);

    private final S3Client cliente;
    private final String bucket;

    public ArmazenamentoR2(S3Client cliente, String bucket) {
        this.cliente = cliente;
        this.bucket = bucket;
    }

    @Override
    public void salvar(String chave, byte[] conteudo, String tipoConteudo) {
        cliente.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(chave)
                        .contentType(tipoConteudo)
                        .build(),
                RequestBody.fromBytes(conteudo));
        log.info("[ARQUIVO] Gravado no R2: {}", chave);
    }

    @Override
    public byte[] ler(String chave) {
        try {
            return cliente.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(chave)
                    .build()).asByteArray();
        } catch (NoSuchKeyException e) {
            log.warn("[ARQUIVO] Arquivo não encontrado no R2: {}", chave);
            throw new ResourceNotFoundException("Arquivo não encontrado.");
        }
    }
}
