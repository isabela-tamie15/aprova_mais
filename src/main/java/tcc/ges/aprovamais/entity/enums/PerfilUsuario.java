package tcc.ges.aprovamais.entity.enums;

public enum PerfilUsuario {
    COORDENADOR("/coordenador/dashboard"),
    ORIENTADOR("/orientador/validacoes"),
    ALUNO("/aluno/dashboard"),
    SECRETARIA("/secretaria/dashboard");

    private final String rotaInicial;

    PerfilUsuario(String rotaInicial) {
        this.rotaInicial = rotaInicial;
    }

    /**
     * Página inicial do perfil, usada para redirecionar o usuário após ações
     * como o aceite de consentimento (/inicio).
     */
    public String getRotaInicial() {
        return rotaInicial;
    }

    /**
     * Converte a authority do Spring Security (ex.: "ROLE_ALUNO") no perfil correspondente.
     */
    public static PerfilUsuario deAuthority(String authority) {
        return valueOf(authority.replaceFirst("^ROLE_", ""));
    }

    /**
     * Perfis administrativos (acesso a convites e dados de vários usuários)
     * são obrigados a usar autenticação de dois fatores. Para os demais, o 2FA é opcional.
     */
    public boolean exigeDoisFatores() {
        return this == COORDENADOR || this == SECRETARIA;
    }
}