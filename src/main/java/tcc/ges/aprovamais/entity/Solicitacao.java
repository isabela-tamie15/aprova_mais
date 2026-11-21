package tcc.ges.aprovamais.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import tcc.ges.aprovamais.entity.enums.StatusSolicitacao;
import tcc.ges.aprovamais.entity.enums.TipoSolicitacao;
import tcc.ges.aprovamais.security.AesEncryptor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
@Entity
@Table(name = "solicitacoes")
public class Solicitacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "estagio_id", nullable = false)
    private Estagio estagio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoSolicitacao tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusSolicitacao status;

    // Tipo de estágio pedido na troca de modalidade. Fica nulo nos outros pedidos
    @ManyToOne
    @JoinColumn(name = "novo_tipo_estagio_id")
    private TipoEstagio novoTipoEstagio;

    // Versão cujo anexo o aluno pediu para excluir. Fica nula nos outros pedidos
    @ManyToOne
    @JoinColumn(name = "versao_id")
    private EntregaVersao versao;

    // O motivo pode trazer dados pessoais, como um problema de saúde, por isso fica cifrado
    @Convert(converter = AesEncryptor.class)
    @Column(columnDefinition = "TEXT", nullable = false)
    private String motivo;

    @Convert(converter = AesEncryptor.class)
    @Column(name = "justificativa_reprovacao", columnDefinition = "TEXT")
    private String justificativaReprovacao;

    // Quem analisou na secretaria e na coordenação, com a data de cada etapa
    @ManyToOne
    @JoinColumn(name = "analista_secretaria_id")
    private Usuario analistaSecretaria;

    @Column(name = "analisada_secretaria_em")
    private OffsetDateTime analisadaSecretariaEm;

    @ManyToOne
    @JoinColumn(name = "analista_coordenacao_id")
    private Usuario analistaCoordenacao;

    @Column(name = "analisada_coordenacao_em")
    private OffsetDateTime analisadaCoordenacaoEm;

    // Último dia para pedir a reabertura, preenchido na aprovação final do trancamento
    @Column(name = "prazo_arrependimento")
    private LocalDate prazoArrependimento;

    @CreatedDate
    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @LastModifiedDate
    @Column(name = "alterado_em", nullable = false)
    private OffsetDateTime alteradoEm;
}
