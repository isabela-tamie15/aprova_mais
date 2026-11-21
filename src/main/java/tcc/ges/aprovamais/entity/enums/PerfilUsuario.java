package tcc.ges.aprovamais.entity.enums;

public enum PerfilUsuario {
    ADMINISTRADOR("/admin/auditoria"),
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

    // Converte a authority do Spring Security, como "ROLE_ALUNO", no perfil correspondente
    public static PerfilUsuario deAuthority(String authority) {
        return valueOf(authority.replaceFirst("^ROLE_", ""));
    }

    // Perfis administrativos, com acesso a convites e aos dados de vários usuários, são obrigados a usar o 2FA. Para os outros ele é opcional
    public boolean exigeDoisFatores() {
        return this == ADMINISTRADOR || this == COORDENADOR || this == SECRETARIA;
    }
}