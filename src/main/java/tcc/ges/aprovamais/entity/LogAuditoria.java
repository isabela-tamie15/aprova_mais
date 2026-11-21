package tcc.ges.aprovamais.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import tcc.ges.aprovamais.entity.enums.PerfilAuditoria;

import java.time.OffsetDateTime;

@Entity
@Table(name = "registros_auditoria")
@Data
@EntityListeners(AuditingEntityListener.class)
public class LogAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    @ToString.Exclude
    private Usuario usuario;

    @Column(name = "email_tentativa")
    private String emailTentativa;

    @Enumerated(EnumType.STRING)
    @Column
    private PerfilAuditoria perfil;

    @Column(nullable = false)
    private String acao;

    @Column(name = "ip_origem")
    private String ipOrigem;

    @Column(columnDefinition = "TEXT")
    private String detalhes;

    @Column(nullable = false)
    private Boolean sucesso;

    @CreatedDate
    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;
}