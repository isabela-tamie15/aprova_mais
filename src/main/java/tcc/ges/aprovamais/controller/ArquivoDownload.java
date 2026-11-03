package tcc.ges.aprovamais.controller;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tcc.ges.aprovamais.service.EntregaService;

import java.nio.charset.StandardCharsets;

// Monta a resposta de download dos arquivos das entregas, igual para o aluno e para o orientador
final class ArquivoDownload {

    private ArquivoDownload() {
    }

    static ResponseEntity<byte[]> resposta(EntregaService.ArquivoEntrega arquivo) {
        return ResponseEntity.ok()
                // attachment: o navegador baixa o arquivo em vez de abrir dentro do sistema
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(arquivo.nome(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                // nosniff: o navegador respeita o Content-Type e não tenta adivinhar o tipo pelo conteúdo
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(MediaType.parseMediaType(arquivo.tipoConteudo()))
                .contentLength(arquivo.conteudo().length)
                .body(arquivo.conteudo());
    }
}
