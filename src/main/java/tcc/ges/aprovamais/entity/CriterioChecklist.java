package tcc.ges.aprovamais.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

/*
   Critério de avaliação de uma tarefa da trilha, cadastrado pelo coordenador. Na correção, o
   orientador marca cada critério como suficiente ou insuficiente. O critério que já foi usado em
   uma correção não é apagado, só desativado, porque as respostas gravadas apontam para ele
*/
@Data
@EntityListeners(AuditingEntityListener.class)
@Entity
@Table(name = "criterios_checklist")
public class CriterioChecklist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "tarefa_id", nullable = false, updatable = false)
    @ToString.Exclude
    private Tarefa tarefa;

    @Column(nullable = false, length = 200)
    private String descricao;

    // Posição do critério no checklist, na ordem em que o orientador vê
    @Column(nullable = false)
    private Integer ordem;

    // Falso quando o coordenador remove um critério que já foi usado. Ele some das próximas correções
    @Column(nullable = false)
    private Boolean ativo = true;

    @CreatedDate
    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @LastModifiedDate
    @Column(name = "alterado_em", nullable = false)
    private OffsetDateTime alteradoEm;
}
