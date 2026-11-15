package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.entity.Aluno;
import tcc.ges.aprovamais.entity.Convite;
import tcc.ges.aprovamais.entity.Matricula;
import tcc.ges.aprovamais.entity.Orientador;
import tcc.ges.aprovamais.entity.PreCadastro;
import tcc.ges.aprovamais.entity.Turma;
import tcc.ges.aprovamais.entity.Usuario;
import tcc.ges.aprovamais.entity.enums.StatusMatricula;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.MatriculaRepository;
import tcc.ges.aprovamais.repository.UsuarioRepository;

import java.util.Optional;

// Primeiro acesso, quando o convidado cria a senha e aceita os termos
@Service
@RequiredArgsConstructor
public class PrimeiroAcessoService {

    private final ConviteService conviteService;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;
    private final ConsentimentoService consentimentoService;
    private final PreCadastroService preCadastroService;
    private final MatriculaRepository matriculaRepository;

    // Conclui o cadastro com a senha definida e o aceite dos termos
    @Transactional
    public void concluirCadastro(String token, String senha, String ip) {
        Convite convite = conviteService.validarToken(token);

        Usuario usuario = usuarioRepository.findByEmail(convite.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        // A conta pendente recebe a senha escolhida, o aceite dos termos e é ativada
        usuario.setSenhaHash(passwordEncoder.encode(senha));
        consentimentoService.aplicarAceite(usuario);
        usuario.setPrimeiroAcesso(false);
        usuario.setAtivo(true);

        // Aluno e orientador recebem os dados do pré-cadastro no lugar dos valores provisórios do convite
        Optional<PreCadastro> preCadastro = preCadastroService.buscarPorEmail(convite.getEmail());
        if (preCadastro.isPresent()) {
            aplicarPreCadastro(usuario, preCadastro.get());
        }

        usuarioRepository.save(usuario);

        // Para o aluno, a matrícula ATIVA é criada na turma do pré-cadastro
        if (usuario instanceof Aluno aluno && preCadastro.isPresent() && preCadastro.get().getTurma() != null) {
            criarMatricula(aluno, preCadastro.get().getTurma(), ip);
        }

        // Marca o convite como usado, assim o token não vale mais
        conviteService.aceitarConvite(token);

        auditoriaService.registrar(
                usuario,
                "PRIMEIRO_ACESSO_ACEITO",
                "Termos aceitos e cadastro concluído. Versão: " + ConsentimentoService.VERSAO_TERMOS_ATUAL,
                ip,
                true
        );
    }

    // Registra a recusa do convite
    @Transactional
    public void recusarCadastro(String token, String ip) {

        // A validação vem antes, então um convite vencido ou já usado nem tem a recusa registrada
        Convite convite = conviteService.validarToken(token);

        // A recusa fica na auditoria pelo e-mail do convite, porque a pessoa ainda não tem conta ativa
        auditoriaService.registrarTentativa(
                convite.getEmail(),
                "PRIMEIRO_ACESSO_RECUSADO",
                "Usuário recusou os termos de uso",
                ip
        );

        conviteService.recusarConvite(token);
    }

    // Copia do pré-cadastro os dados que identificam a pessoa na instituição
    private void aplicarPreCadastro(Usuario usuario, PreCadastro preCadastro) {
        usuario.setNome(preCadastro.getNome());
        if (usuario instanceof Aluno aluno) {
            aluno.setRgm(preCadastro.getIdentificadorInstitucional());
        } else if (usuario instanceof Orientador orientador) {
            orientador.setMatriculaInstitucional(preCadastro.getIdentificadorInstitucional());
        }
    }

    //Cria a matrícula do aluno na turma do pré-cadastro
    private void criarMatricula(Aluno aluno, Turma turma, String ip) {
        if (matriculaRepository.findByAlunoIdAndTurmaId(aluno.getId(), turma.getId()).isPresent()) {
            return;
        }
        Matricula matricula = new Matricula();
        matricula.setAluno(aluno);
        matricula.setTurma(turma);
        matricula.setStatus(StatusMatricula.ATIVA);
        Matricula salva = matriculaRepository.save(matricula);

        auditoriaService.registrar(aluno, "MATRICULA_CRIADA",
                "Matrícula id=" + salva.getId() + " criada na turma id=" + turma.getId() + " no aceite do convite",
                ip, true);
    }
}
