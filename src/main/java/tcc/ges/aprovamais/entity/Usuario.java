package tcc.ges.aprovamais.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import tcc.ges.aprovamais.entity.enums.PerfilUsuario;
import tcc.ges.aprovamais.security.AesEncryptor;

import java.time.OffsetDateTime;


// @Getter e @Setter no lugar do @Data, para o toString não mostrar senha, segredo do 2FA e tokens
@Inheritance(strategy = InheritanceType.JOINED)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Entity
@Table(name = "usuarios")
public abstract class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "senha_hash", nullable = false)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PerfilUsuario perfil;

    @Column(name = "dois_fatores_ativo")
    private Boolean doisFatoresAtivo = false;

    @Convert(converter = AesEncryptor.class)
    @Column(name = "dois_fatores_segredo")
    private String doisFatoresSegredo;

    // Passo de 30 segundos do último código TOTP aceito, para cada código valer uma vez só
    @Column(name = "ultimo_passo_totp")
    private Long ultimoPassoTotp;

    @Column(name = "token_pre_autenticacao")
    private String tokenPreAutenticacao;

    @Column(name = "expiracao_pre_autenticacao")
    private OffsetDateTime expiracaoPreAutenticacao;

    @Column(name = "tentativas_falhas")
    private Integer tentativasFalhas = 0;

    @Column(name = "conta_bloqueada")
    private Boolean contaBloqueada = false;

    @Column(name = "momento_bloqueio")
    private OffsetDateTime momentoBloqueio;

    @Column(name = "token_recuperacao")
    private String tokenRecuperacao;

    @Column(name = "expiracao_token_recuperacao")
    private OffsetDateTime expiracaoTokenRecuperacao;

    @Column(name = "consentimento_dado")
    private Boolean consentimentoDado = false;

    @Column(name = "data_consentimento")
    private OffsetDateTime dataConsentimento;

    @Column(name = "versao_consentimento")
    private String versaoConsentimento;

    @Column(name = "primeiro_acesso")
    private Boolean primeiroAcesso = true;

    @Column(nullable = false)
    private Boolean ativo = true;

    // Versão da sessão gravada no JWT. Somar 1 encerra todas as sessões abertas da conta (nulo vale como zero)
    @Column(name = "versao_sessao")
    private Integer versaoSessao = 0;

    @CreatedDate
    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @LastModifiedDate
    @Column(name = "alterado_em", nullable = false)
    private OffsetDateTime alteradoEm;

    // Versão atual da sessão, com nulo valendo zero
    public int versaoSessaoAtual() {
        return versaoSessao == null ? 0 : versaoSessao;
    }

    // Encerra todas as sessões abertas da conta, e os tokens emitidos antes deixam de valer
    public void encerrarSessoes() {
        versaoSessao = versaoSessaoAtual() + 1;
    }

    // Só o id e o perfil, para senha, segredo do 2FA, tokens e e-mail não aparecerem em log
    @Override
    public String toString() {
        return getClass().getSimpleName() + "(id=" + id + ", perfil=" + perfil + ")";
    }
}
