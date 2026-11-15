package tcc.ges.aprovamais.service;

import tcc.ges.aprovamais.entity.enums.TipoArquivo;
import tcc.ges.aprovamais.exception.DadoInvalidoException;

import java.util.Locale;

/*
   Confere o arquivo da entrega pelo formato da extensão e pelos primeiros bytes do conteúdo,
   para um .exe renomeado para .pdf não passar. Fica separado do service para ser testado sem Spring
*/
public final class ValidadorArquivoEntrega {

    public static final long TAMANHO_MAXIMO_BYTES = 10L * 1024 * 1024;

    public static final String FORMATOS_ACEITOS = "PDF, DOCX, XLSX, PPTX, JPG, JPEG ou PNG";

    private static final byte[] ASSINATURA_PDF = {'%', 'P', 'D', 'F', '-'};
    // DOCX, XLSX e PPTX são arquivos ZIP por dentro
    private static final byte[] ASSINATURA_ZIP = {'P', 'K', 3, 4};
    private static final byte[] ASSINATURA_JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] ASSINATURA_PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private ValidadorArquivoEntrega() {
    }

    // Devolve o tipo do arquivo ou recusa com a mensagem que vai para a tela
    public static TipoArquivo validar(String nomeOriginal, byte[] conteudo) {
        if (conteudo == null || conteudo.length == 0) {
            throw new DadoInvalidoException("Selecione um arquivo para enviar.");
        }
        if (conteudo.length > TAMANHO_MAXIMO_BYTES) {
            throw new DadoInvalidoException("O arquivo deve ter no máximo 10 MB.");
        }

        TipoArquivo tipoArquivo = tipoPelaExtensao(nomeOriginal);
        if (tipoArquivo == null) {
            throw new DadoInvalidoException("Formato não aceito. Envie um arquivo " + FORMATOS_ACEITOS + ".");
        }
        if (!comecaCom(conteudo, assinatura(tipoArquivo))) {
            throw new DadoInvalidoException("O conteúdo do arquivo não corresponde a um "
                    + tipoArquivo.name() + ". Confira o arquivo e envie de novo.");
        }
        return tipoArquivo;
    }

    // Nome gravado e mostrado no download, sem pastas, sem caracteres de controle e com no máximo 200 caracteres
    public static String limparNome(String nomeOriginal) {
        String nomeDoArquivo = nomeOriginal == null ? "" : nomeOriginal;
        nomeDoArquivo = nomeDoArquivo.substring(Math.max(nomeDoArquivo.lastIndexOf('/'), nomeDoArquivo.lastIndexOf('\\')) + 1);
        nomeDoArquivo = nomeDoArquivo.replaceAll("[\\p{Cntrl}\"]", "").trim();
        if (nomeDoArquivo.isEmpty()) {
            nomeDoArquivo = "arquivo";
        }
        if (nomeDoArquivo.length() > 200) {
            int ponto = nomeDoArquivo.lastIndexOf('.');
            String extensao = ponto > 0 ? nomeDoArquivo.substring(ponto) : "";
            nomeDoArquivo = nomeDoArquivo.substring(0, 200 - extensao.length()) + extensao;
        }
        return nomeDoArquivo;
    }

    // Content-Type usado no armazenamento e no download
    public static String tipoConteudo(TipoArquivo tipoArquivo) {
        return switch (tipoArquivo) {
            case PDF -> "application/pdf";
            case DOCX -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case XLSX -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case PPTX -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case JPG, JPEG -> "image/jpeg";
            case PNG -> "image/png";
        };
    }

    private static TipoArquivo tipoPelaExtensao(String nomeDoArquivo) {
        if (nomeDoArquivo == null) {
            return null;
        }
        int ponto = nomeDoArquivo.lastIndexOf('.');
        if (ponto < 0 || ponto == nomeDoArquivo.length() - 1) {
            return null;
        }
        String extensao = nomeDoArquivo.substring(ponto + 1).toUpperCase(Locale.ROOT);
        for (TipoArquivo tipoArquivo : TipoArquivo.values()) {
            if (tipoArquivo.name().equals(extensao)) {
                return tipoArquivo;
            }
        }
        return null;
    }

    private static byte[] assinatura(TipoArquivo tipoArquivo) {
        return switch (tipoArquivo) {
            case PDF -> ASSINATURA_PDF;
            case DOCX, XLSX, PPTX -> ASSINATURA_ZIP;
            case JPG, JPEG -> ASSINATURA_JPG;
            case PNG -> ASSINATURA_PNG;
        };
    }

    private static boolean comecaCom(byte[] conteudo, byte[] assinatura) {
        if (conteudo.length < assinatura.length) {
            return false;
        }
        for (int posicao = 0; posicao < assinatura.length; posicao++) {
            if (conteudo[posicao] != assinatura[posicao]) {
                return false;
            }
        }
        return true;
    }
}
