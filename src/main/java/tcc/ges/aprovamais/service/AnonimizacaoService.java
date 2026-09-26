package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.entity.Usuario;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.AlunoRepository;
import tcc.ges.aprovamais.repository.OrientadorRepository;
import tcc.ges.aprovamais.repository.SecretariaRepository;
import tcc.ges.aprovamais.repository.UsuarioRepository;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnonimizacaoService {

    private final UsuarioRepository usuarioRepository;
    private final AlunoRepository alunoRepository;
    private final OrientadorRepository orientadorRepository;
    private final SecretariaRepository secretariaRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;

    @Transactional
    public void anonimizar(String email, String ipOrigem) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        String sufixo = UUID.randomUUID().toString().substring(0, 8);

        usuario.setNome("USUÁRIO REMOVIDO");
        usuario.setEmail("removido_" + sufixo + "@anonimizado.br");
        usuario.setSenhaHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        usuario.setAtivo(false);
        usuario.setConsentimentoDado(false);
        usuario.setTokenRecuperacao(null);
        usuario.setDoisFatoresSegredo(null);

        switch (usuario.getPerfil()) {
            case ALUNO -> alunoRepository.findById(usuario.getId())
                    .ifPresent(aluno -> {
                        aluno.setRgm("REMOVIDO_" + sufixo);
                        alunoRepository.save(aluno);
                    });
            case ORIENTADOR -> orientadorRepository.findById(usuario.getId())
                    .ifPresent(orientador -> {
                        orientador.setMatriculaInstitucional("REMOVIDO_" + sufixo);
                        orientador.setDepartamento("REMOVIDO");
                        orientadorRepository.save(orientador);
                    });
            case SECRETARIA -> secretariaRepository.findById(usuario.getId())
                    .ifPresent(secretaria -> {
                        secretaria.setSetor("REMOVIDO");
                        secretariaRepository.save(secretaria);
                    });
            case COORDENADOR -> {
                // coordenador não tem campos extras além do Usuario
            }
        }

        usuarioRepository.save(usuario);

        auditoriaService.registrar(
                usuario,
                "ANONIMIZACAO_EXECUTADA",
                "Dados pessoais anonimizados por solicitação do titular",
                ipOrigem,
                true
        );
    }
}