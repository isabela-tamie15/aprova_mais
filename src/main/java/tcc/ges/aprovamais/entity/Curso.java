package tcc.ges.aprovamais.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.List;

@Entity
@Data
@EntityListeners(AuditingEntityListener.class)
@Table(name = "cursos")
public class Curso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(name = "codigo_mec", nullable = false, unique = true)
    private String codigoMec;

    /*
       Coordenador responsável pelo curso (RN20). Quem cadastra o curso vira o
       coordenador dele. Fica nulo nos cursos criados por script antes desse campo
       existir, que precisam ser vinculados com o script de ajuste do banco
    */
    @ManyToOne
    @JoinColumn(name = "coordenador_id")
    @ToString.Exclude
    private Coordenador coordenador;

    @OneToMany(mappedBy = "curso")
    @ToString.Exclude
    private List<TipoEstagio> tiposEstagio;

    @CreatedDate
    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @LastModifiedDate
    @Column(name = "alterado_em", nullable = false)
    private OffsetDateTime alteradoEm;}