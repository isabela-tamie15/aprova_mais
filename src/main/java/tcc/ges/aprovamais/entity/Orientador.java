package tcc.ges.aprovamais.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@Table(name = "orientadores")
@Entity
public class Orientador extends Usuario{

    @Column(name = "matricula_institucional", nullable = false, unique = true)
    private String matriculaInstitucional;

    @Column
    private String departamento;

}
