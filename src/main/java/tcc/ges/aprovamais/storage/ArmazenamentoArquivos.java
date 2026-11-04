package tcc.ges.aprovamais.storage;

/*
   Onde os arquivos das entregas ficam guardados. Existem duas implementações:
   disco local (desenvolvimento e Docker, numa pasta montada como volume) e
   Cloudflare R2 (produção). A escolha é feita no ArmazenamentoConfig, pelas
   variáveis R2_*. O resto do sistema só conhece essa interface
*/
public interface ArmazenamentoArquivos {

    // Grava o conteúdo na chave informada. A chave é gerada pelo sistema, nunca vem do usuario
    void salvar(String chave, byte[] conteudo, String tipoConteudo);

    // Lê o conteúdo de uma chave gravada antes
    byte[] ler(String chave);
}
