package tcc.ges.aprovamais.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import tcc.ges.aprovamais.storage.ArmazenamentoArquivos;
import tcc.ges.aprovamais.storage.ArmazenamentoLocal;
import tcc.ges.aprovamais.storage.ArmazenamentoR2;

import java.net.URI;
import java.nio.file.Path;

/*
   Escolhe onde os arquivos das entregas são guardados. Com as variáveis
   R2_ENDPOINT, R2_ACCESS_KEY, R2_SECRET_KEY e R2_BUCKET preenchidas, usa o
   Cloudflare R2, sem essas variáveis ele usa a pasta local ARMAZENAMENTO_DIRETORIO (padrão "uploads", que no Docker é um volume)
*/
@Configuration
public class ArmazenamentoConfig {

    private static final Logger log = LoggerFactory.getLogger(ArmazenamentoConfig.class);

    @Bean
    public ArmazenamentoArquivos armazenamentoArquivos(
            @Value("${r2.endpoint:}") String endpoint,
            @Value("${r2.access-key:}") String chaveAcesso,
            @Value("${r2.secret-key:}") String chaveSecreta,
            @Value("${r2.bucket:}") String bucket,
            @Value("${r2.region:auto}") String regiao,
            @Value("${aprova.armazenamento.diretorio:uploads}") String diretorio) {

        if (endpoint.isBlank() || chaveAcesso.isBlank() || chaveSecreta.isBlank() || bucket.isBlank()) {
            log.info("[ARQUIVO] Arquivos das entregas no disco local, pasta {}", diretorio);
            return new ArmazenamentoLocal(Path.of(diretorio));
        }

        S3Client cliente = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(regiao))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(chaveAcesso, chaveSecreta)))
                // O R2 usa o bucket no caminho da URL, e não no subdomínio
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
        log.info("[ARQUIVO] Arquivos das entregas no Cloudflare R2, bucket {}", bucket);
        return new ArmazenamentoR2(cliente, bucket);
    }
}
