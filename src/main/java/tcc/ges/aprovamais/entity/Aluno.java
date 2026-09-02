package tcc.ges.aprovamais.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@Table(name = "alunos")
@Entity
public class Aluno extends Usuario{

    @Column(nullable = false, unique = true)
    private String rgm;
}
