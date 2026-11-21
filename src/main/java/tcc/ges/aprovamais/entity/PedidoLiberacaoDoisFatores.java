package tcc.ges.aprovamais.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import tcc.ges.aprovamais.entity.enums.StatusPedidoLiberacao;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "pedidos_liberacao_2fa")
public class PedidoLiberacaoDoisFatores {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    @ToString.Exclude
    private Usuario usuario;

    @Column(length = 500)
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusPedidoLiberacao status = StatusPedidoLiberacao.PENDENTE;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decidido_por_id")
    @ToString.Exclude
    private Usuario decididoPor;

    @Column(name = "decidido_em")
    private OffsetDateTime decididoEm;

    @Column(length = 1000)
    private String justificativa;
}
