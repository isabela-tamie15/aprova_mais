package tcc.ges.aprovamais.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import tcc.ges.aprovamais.entity.enums.PerfilDestino;
import tcc.ges.aprovamais.entity.enums.StatusConvite;

import java.time.OffsetDateTime;

@Data
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "convites")
public class Convite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(name = "token_convite", nullable = false, unique = true)
    private String tokenConvite;

    @Enumerated(EnumType.STRING)
    @Column(name = "perfil_destino", nullable = false)
    private PerfilDestino perfilDestino;

    @ManyToOne
    @JoinColumn(name = "coordenador_id", nullable = false)
    @ToString.Exclude
    private Usuario remetente;

    @ManyToOne
    @JoinColumn(name = "curso_id")
    @ToString.Exclude
    private Curso curso;

    @ManyToOne
    @JoinColumn(name = "turma_id")
    @ToString.Exclude
    private Turma turma;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusConvite status;

    @Column(name = "expiracao_token", nullable = false)
    private OffsetDateTime expiracaoToken;

    @CreatedDate
    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @LastModifiedDate
    @Column(name = "alterado_em", nullable = false)
    private OffsetDateTime alteradoEm;
}