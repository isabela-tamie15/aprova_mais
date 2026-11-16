package tcc.ges.aprovamais.entity.enums;

// Parâmetros que o administrador pode ajustar, com o valor padrão e os limites aceitos
public enum ParametroSistema {

    PRAZO_ARREPENDIMENTO_DIAS("Prazo de arrependimento do trancamento", "dias", 7, 1, 30),
    TENTATIVAS_ANTES_BLOQUEIO("Tentativas erradas antes do bloqueio", "tentativas", 5, 3, 10),
    MINUTOS_BLOQUEIO("Tempo de bloqueio da conta", "minutos", 5, 1, 60),
    HORAS_VALIDADE_CONVITE("Validade do convite", "horas", 24, 1, 168),
    MINUTOS_PRE_AUTENTICACAO("Validade do token de pré-autenticação", "minutos", 2, 1, 10),
    HORAS_VALIDADE_SESSAO("Validade da sessão", "horas", 8, 1, 24),
    DIAS_AVISO_AMARELO("Antecedência do aviso de prazo (amarelo)", "dias", 30, 5, 90),
    DIAS_AVISO_VERMELHO("Dias para o aviso de prazo ficar vermelho", "dias", 10, 1, 30),
    MESES_RETENCAO_AUDITORIA("Retenção dos registros de auditoria", "meses", 6, 6, 60);

    private final String descricao;
    private final String unidade;
    private final int padrao;
    private final int minimo;
    private final int maximo;

    ParametroSistema(String descricao, String unidade, int padrao, int minimo, int maximo) {
        this.descricao = descricao;
        this.unidade = unidade;
        this.padrao = padrao;
        this.minimo = minimo;
        this.maximo = maximo;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getUnidade() {
        return unidade;
    }

    public int getPadrao() {
        return padrao;
    }

    public int getMinimo() {
        return minimo;
    }

    public int getMaximo() {
        return maximo;
    }
}
