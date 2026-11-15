package tcc.ges.aprovamais.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import tcc.ges.aprovamais.security.AesEncryptor;

import java.time.OffsetDateTime;


/*
   Cada decisão do orientador sobre um estágio, aprovação ou rejeição, vira uma linha que nunca é
   alterada nem apagada. Assim a justificativa de uma rejeição continua guardada mesmo depois
   que o estágio é aprovado
*/
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

    // Opinião do orientador, cifrada em repouso como a justificativa do estágio
    @Convert(converter = AesEncryptor.class)
    @Column(columnDefinition = "TEXT", updatable = false)
    private String justificativa;

    @CreatedDate
    @Column(name = "data_decisao", nullable = false, updatable = false)
    private OffsetDateTime dataDecisao;
}
