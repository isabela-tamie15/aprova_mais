package tcc.ges.aprovamais.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.List;

@Data
@EntityListeners(AuditingEntityListener.class)
@Entity
@Table(name = "trilhas")
public class Trilha {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "tipo_estagio_id", nullable = false, unique = true)
    private TipoEstagio tipoEstagio;

    @Column(nullable = false)
    private String nome;

    @Column
    private String descricao;

    @ToString.Exclude
    @OneToMany(mappedBy = "trilha", cascade = CascadeType.ALL)
    private List<Tarefa> tarefas;

    @CreatedDate
    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @LastModifiedDate
    @Column(name = "alterado_em", nullable = false)
    private LocalDateTime alteradoEm;
}
