package tcc.ges.aprovamais.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import tcc.ges.aprovamais.entity.enums.StatusEstagio;
import tcc.ges.aprovamais.entity.enums.MotivoEncerramento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EntityListeners(AuditingEntityListener.class)
@Entity
@Table(name = "estagios")
public class Estagio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "matricula_id", nullable = false)
    @ToString.Exclude
    private Matricula matricula;

    @ManyToOne
    @JoinColumn(name = "orientador_id")
    @ToString.Exclude
    private Orientador orientador;

    @ManyToOne
    @JoinColumn(name = "tipo_estagio_id", nullable = false)
    @ToString.Exclude
    private TipoEstagio tipoEstagio;

    @Column(name = "nome_empresa")
    private String nomeEmpresa;

    @Column(name = "local_empresa")
    private String localEmpresa;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "data_prevista_conclusao")
    private LocalDate dataPrevistaConclusao;

    @Column(name = "carga_horaria_necessaria", nullable = false)
    private BigDecimal cargaHorariaNecessaria;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusEstagio status;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_encerramento")
    private MotivoEncerramento motivoEncerramento;

    @Column(name = "justificativa_rejeicao")
    private String justificativaRejeicao;

    @CreatedDate
    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @LastModifiedDate
    @Column(name = "alterado_em", nullable = false)
    private LocalDateTime alteradoEm;
}