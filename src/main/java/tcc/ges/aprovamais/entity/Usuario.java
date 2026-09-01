package tcc.ges.aprovamais.entity;


import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import tcc.ges.aprovamais.entity.enums.PerfilUsuario;

import java.time.OffsetDateTime;


@Inheritance(strategy = InheritanceType.JOINED)
@EntityListeners(AuditingEntityListener.class)
@Data
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

    @Column(name = "dois_fatores_segredo")
    private String doisFatoresSegredo;

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

    @CreatedDate
    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @LastModifiedDate
    @Column(name = "alterado_em", nullable = false)
    private OffsetDateTime alteradoEm;
}