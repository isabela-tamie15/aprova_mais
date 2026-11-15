package tcc.ges.aprovamais.storage;

/*
   Onde os arquivos das entregas ficam guardados. Existem duas implementações, o disco local
   (desenvolvimento e Docker) e o Cloudflare R2 (produção). A escolha fica no ArmazenamentoConfig,
   e o resto do sistema só conhece esta interface
*/
public interface ArmazenamentoArquivos {

    // Grava o conteúdo na chave informada. A chave é gerada pelo sistema, nunca vem do usuario
    void salvar(String chave, byte[] conteudo, String tipoConteudo);

    // Lê o conteúdo de uma chave gravada antes
    byte[] ler(String chave);
}
