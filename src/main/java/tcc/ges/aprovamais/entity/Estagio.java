package tcc.ges.aprovamais.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import tcc.ges.aprovamais.entity.enums.MotivoEncerramento;
import tcc.ges.aprovamais.entity.enums.StatusEstagio;
import tcc.ges.aprovamais.security.AesEncryptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

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

    @Column(name = "nome_empresa", nullable = false)
    private String nomeEmpresa;

    @Column(name = "local_empresa")
    private String localEmpresa;

    // CNPJ só com números e letras, sem pontuação. Fica nulo nos estágios antigos
    @Column(name = "cnpj_empresa", length = 14)
    private String cnpjEmpresa;


    //Situação cadastral que a Receita devolveu no cadastro, fica nulo quando a consulta não foi possível, aí o orientador confere manualmente
    @Column(name = "situacao_cnpj", length = 40)
    private String situacaoCnpj;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "data_prevista_conclusao")
    private LocalDate dataPrevistaConclusao;

    // Carga total e jornada semanal copiadas do tipo no cadastro. Mudar o tipo depois não afeta este estágio
    @Column(name = "carga_horaria_necessaria", nullable = false)
    private BigDecimal cargaHorariaNecessaria;

    @Column(name = "jornada_semanal", nullable = false)
    private BigDecimal jornadaSemanal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusEstagio status;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_encerramento")
    private MotivoEncerramento motivoEncerramento;

    // Texto livre do orientador sobre o aluno, cifrado em repouso com AES-256-GCM. É TEXT porque o valor cifrado é maior que o original
    @Convert(converter = AesEncryptor.class)
    @Column(name = "justificativa_rejeicao", columnDefinition = "TEXT")
    private String justificativaRejeicao;

    // Dia em que o estágio foi concluído ou encerrado
    @Column(name = "data_finalizacao")
    private LocalDate dataFinalizacao;

    // Observação de quem encerrou o estágio, cifrada como a justificativa da rejeição
    @Convert(converter = AesEncryptor.class)
    @Column(name = "observacao_encerramento", columnDefinition = "TEXT")
    private String observacaoEncerramento;

    // Dias em que o estágio ficou suspenso por trancamento e depois foi reaberto. Não contam na carga horária
    @Column(name = "dias_suspensos")
    private Integer diasSuspensos;

    // Dia em que o estágio foi suspenso. A carga para de contar nessa data
    @Column(name = "suspenso_desde")
    private LocalDate suspensoDesde;

    @CreatedDate
    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @LastModifiedDate
    @Column(name = "alterado_em", nullable = false)
    private OffsetDateTime alteradoEm;
}