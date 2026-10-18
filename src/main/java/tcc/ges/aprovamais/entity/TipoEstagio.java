package tcc.ges.aprovamais.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@EntityListeners(AuditingEntityListener.class)
@Entity
@Table(name = "tipos_estagio")
public class TipoEstagio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "curso_id", nullable = false)
    private Curso curso;

    @Column(nullable = false)
    private String nome;

    @Column
    private String descricao;

    //Carga horária total exigida pelo curso nesse tipo
    @Column(name = "carga_horaria_necessaria", nullable = false)
    private BigDecimal cargaHorariaNecessaria;

    //quantas horas por semana esse tipo abate
    @Column(name = "jornada_semanal", nullable = false)
    private BigDecimal jornadaSemanal;

    @ToString.Exclude
    @OneToOne(mappedBy = "tipoEstagio", cascade = CascadeType.ALL)
    private Trilha trilha;

    @CreatedDate
    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @LastModifiedDate
    @Column(name = "alterado_em", nullable = false)
    private OffsetDateTime alteradoEm;
}
