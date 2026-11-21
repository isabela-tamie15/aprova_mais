package tcc.ges.aprovamais.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tcc.ges.aprovamais.entity.PedidoLiberacaoDoisFatores;
import tcc.ges.aprovamais.entity.enums.PerfilUsuario;
import tcc.ges.aprovamais.entity.enums.StatusPedidoLiberacao;

import java.util.Collection;
import java.util.List;

public interface PedidoLiberacaoDoisFatoresRepository extends JpaRepository<PedidoLiberacaoDoisFatores, Long> {

    boolean existsByUsuarioIdAndStatus(Long usuarioId, StatusPedidoLiberacao status);

    @Query("""
            select p from PedidoLiberacaoDoisFatores p
            join fetch p.usuario u
            where p.status = :status and u.perfil in :perfis
            order by p.criadoEm asc
            """)
    List<PedidoLiberacaoDoisFatores> listarPorStatusEPerfis(@Param("status") StatusPedidoLiberacao status,
                                                           @Param("perfis") Collection<PerfilUsuario> perfis);
}
