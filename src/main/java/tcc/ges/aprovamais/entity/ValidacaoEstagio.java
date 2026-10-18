package tcc.ges.aprovamais.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import tcc.ges.aprovamais.security.AesEncryptor;

import java.time.OffsetDateTime;


//Cada decisão do orientador sobre um estagio vira uma linha aqui, aprovação ou rejeição. O registro nunca é alterado nem apagado, assim a justificativa de uma rejeição continua guardada mesmo depois que o aluno reenvia e o estágio é aprovado (RN41 e RF22)
@Data
@EntityListeners(AuditingEntityListener.class)
@Entity
@Table(name = "validacoes_estagio")
public class ValidacaoEstagio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "estagio_id", nullable = false, updatable = false)
    @ToString.Exclude
    private Estagio estagio;

    @ManyToOne
    @JoinColumn(name = "orientador_id", nullable = false, updatable = false)
    @ToString.Exclude
    private Orientador orientador;

    @Column(nullable = false, updatable = false)
    private Boolean aprovado;

    // Mesma proteção da justificativa do estágio: opinião do orientador, cifrada em repouso
    @Convert(converter = AesEncryptor.class)
    @Column(columnDefinition = "TEXT", updatable = false)
    private String justificativa;

    @CreatedDate
    @Column(name = "data_decisao", nullable = false, updatable = false)
    private OffsetDateTime dataDecisao;
}
