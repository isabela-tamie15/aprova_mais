package tcc.ges.aprovamais.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import tcc.ges.aprovamais.entity.enums.StatusEntrega;

import java.time.OffsetDateTime;

/*
   Entrega de uma tarefa da trilha pelo aluno. Cada estágio tem no máximo uma entrega por tarefa, e os arquivos enviados ficam nas versões dela
   (EntregaVersao).
   O status é o da última versão pode ser ENVIADA, EM_REVISAO, APROVADA ou DEVOLVIDA
*/
@Data
@EntityListeners(AuditingEntityListener.class)
@Entity
@Table(name = "entregas",
        uniqueConstraints = @UniqueConstraint(name = "uk_entrega_estagio_tarefa",
                columnNames = {"estagio_id", "tarefa_id"}))
public class Entrega {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // A entrega pertence ao estágio, e não ao aluno: um estágio novo começa sem entregas (RN57)
    @ManyToOne
    @JoinColumn(name = "estagio_id", nullable = false, updatable = false)
    @ToString.Exclude
    private Estagio estagio;

    @ManyToOne
    @JoinColumn(name = "tarefa_id", nullable = false, updatable = false)
    @ToString.Exclude
    private Tarefa tarefa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusEntrega status;

    @CreatedDate
    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @LastModifiedDate
    @Column(name = "alterado_em", nullable = false)
    private OffsetDateTime alteradoEm;
}
