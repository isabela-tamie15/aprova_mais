package tcc.ges.aprovamais.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

@Data
@EntityListeners(AuditingEntityListener.class)
@Entity
// Cada turma tem um orientador só. A restrição única fica no turma_id, e o mesmo orientador pode ficar com várias turmas
@Table(name = "orientadores_turmas",
        uniqueConstraints = @UniqueConstraint(name = "uk_orientador_turma_turma", columnNames = {"turma_id"}))
public class OrientadorTurma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "orientador_id", nullable = false)
    private Orientador orientador;

    @ManyToOne
    @JoinColumn(name = "turma_id", nullable = false)
    private Turma turma;

    @CreatedDate
    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;
}