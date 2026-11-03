package tcc.ges.aprovamais.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import tcc.ges.aprovamais.entity.enums.StatusEntrega;
import tcc.ges.aprovamais.entity.enums.TipoArquivo;
import tcc.ges.aprovamais.security.AesEncryptor;

import java.time.OffsetDateTime;

/*
   Cada envio do aluno vira uma versão numerada e imutável.
   Os campos do envio são updatable = false: nem o código consegue mudar o arquivo, o número ou o comentário de uma versão gravada. Os campos da avaliação começam vazios
   e são preenchidos uma única vez, quando o orientador aprova ou devolve
*/
@Data
@EntityListeners(AuditingEntityListener.class)
@Entity
@Table(name = "entrega_versoes",
        uniqueConstraints = @UniqueConstraint(name = "uk_versao_entrega_numero",
                columnNames = {"entrega_id", "numero"}))
public class EntregaVersao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "entrega_id", nullable = false, updatable = false)
    @ToString.Exclude
    private Entrega entrega;

    // 1 no primeiro envio, depois o maior número anterior mais 1
    @Column(nullable = false, updatable = false)
    private Integer numero;

    // Nome original do arquivo, só para exibir e para o download
    @Column(name = "nome_arquivo", nullable = false, updatable = false)
    private String nomeArquivo;

    // Onde o arquivo ficou guardado (disco local ou Cloudflare R2). Nunca vai para o navegador
    @Column(name = "chave_arquivo", nullable = false, updatable = false, length = 500)
    private String chaveArquivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_arquivo", nullable = false, updatable = false, length = 10)
    private TipoArquivo tipoArquivo;

    @Column(name = "tamanho_bytes", nullable = false, updatable = false)
    private Long tamanhoBytes;

    // Comentário opcional do aluno, cifrado como os outros textos livres
    @Convert(converter = AesEncryptor.class)
    @Column(name = "comentario_aluno", columnDefinition = "TEXT", updatable = false)
    private String comentarioAluno;

    @CreatedDate
    @Column(name = "enviada_em", nullable = false, updatable = false)
    private OffsetDateTime enviadaEm;

    // Avaliação do orientador, pode ser APROVADA ou DEVOLVIDA. Nula enquanto a versão não foi avaliada
    @Enumerated(EnumType.STRING)
    @Column(name = "resultado", length = 20)
    private StatusEntrega resultado;

    // Feedback do orientador, obrigatório na devolução. Cifrado tbm
    @Convert(converter = AesEncryptor.class)
    @Column(columnDefinition = "TEXT")
    private String feedback;

    @ManyToOne
    @JoinColumn(name = "avaliador_id")
    @ToString.Exclude
    private Orientador avaliador;

    @Column(name = "avaliada_em")
    private OffsetDateTime avaliadaEm;
}
