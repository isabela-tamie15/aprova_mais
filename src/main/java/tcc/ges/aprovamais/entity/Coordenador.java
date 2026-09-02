package tcc.ges.aprovamais.entity;


import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "coordenadores")
@Data
public class Coordenador extends Usuario{
}
