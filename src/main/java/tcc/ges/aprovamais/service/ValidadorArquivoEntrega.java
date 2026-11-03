package tcc.ges.aprovamais.service;

import tcc.ges.aprovamais.entity.enums.TipoArquivo;
import tcc.ges.aprovamais.exception.DadoInvalidoException;

import java.util.Locale;

/*
   Confere o arquivo enviado na entrega (RN56): formato pela extensão e pelos
   primeiros bytes do conteúdo, para um .exe renomeado para .pdf não passar.
   Separado do service para dar para testar sem Spring
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

    // Devolve o tipo do arquivo ou recusa com a mensagem que vai para a tela (CA02 do RF33)
    public static TipoArquivo validar(String nomeOriginal, byte[] conteudo) {
        if (conteudo == null || conteudo.length == 0) {
            throw new DadoInvalidoException("Selecione um arquivo para enviar.");
        }
        if (conteudo.length > TAMANHO_MAXIMO_BYTES) {
            throw new DadoInvalidoException("O arquivo deve ter no máximo 10 MB.");
        }

        TipoArquivo tipo = tipoPelaExtensao(nomeOriginal);
        if (tipo == null) {
            throw new DadoInvalidoException("Formato não aceito. Envie um arquivo " + FORMATOS_ACEITOS + ".");
        }
        if (!comecaCom(conteudo, assinatura(tipo))) {
            throw new DadoInvalidoException("O conteúdo do arquivo não corresponde a um "
                    + tipo.name() + ". Confira o arquivo e envie de novo.");
        }
        return tipo;
    }

    /*
       Nome que fica gravado e aparece no download: só o nome, sem pastas, sem
       caracteres de controle e com no máximo 200 caracteres
    */
    public static String limparNome(String nomeOriginal) {
        String nome = nomeOriginal == null ? "" : nomeOriginal;
        nome = nome.substring(Math.max(nome.lastIndexOf('/'), nome.lastIndexOf('\\')) + 1);
        nome = nome.replaceAll("[\\p{Cntrl}\"]", "").trim();
        if (nome.isEmpty()) {
            nome = "arquivo";
        }
        if (nome.length() > 200) {
            int ponto = nome.lastIndexOf('.');
            String extensao = ponto > 0 ? nome.substring(ponto) : "";
            nome = nome.substring(0, 200 - extensao.length()) + extensao;
        }
        return nome;
    }

    // Content-Type usado no armazenamento e no download
    public static String tipoConteudo(TipoArquivo tipo) {
        return switch (tipo) {
            case PDF -> "application/pdf";
            case DOCX -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case XLSX -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case PPTX -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case JPG, JPEG -> "image/jpeg";
            case PNG -> "image/png";
        };
    }

    private static TipoArquivo tipoPelaExtensao(String nome) {
        if (nome == null) {
            return null;
        }
        int ponto = nome.lastIndexOf('.');
        if (ponto < 0 || ponto == nome.length() - 1) {
            return null;
        }
        String extensao = nome.substring(ponto + 1).toUpperCase(Locale.ROOT);
        for (TipoArquivo tipo : TipoArquivo.values()) {
            if (tipo.name().equals(extensao)) {
                return tipo;
            }
        }
        return null;
    }

    private static byte[] assinatura(TipoArquivo tipo) {
        return switch (tipo) {
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
        for (int i = 0; i < assinatura.length; i++) {
            if (conteudo[i] != assinatura[i]) {
                return false;
            }
        }
        return true;
    }
}
