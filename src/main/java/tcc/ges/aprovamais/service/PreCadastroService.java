package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.dto.PreCadastroRequest;
import tcc.ges.aprovamais.dto.PreCadastroResponse;
import tcc.ges.aprovamais.entity.Coordenador;
import tcc.ges.aprovamais.entity.PreCadastro;
import tcc.ges.aprovamais.entity.Turma;
import tcc.ges.aprovamais.entity.Usuario;
import tcc.ges.aprovamais.entity.enums.PerfilDestino;
import tcc.ges.aprovamais.entity.enums.StatusConvite;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.AlunoRepository;
import tcc.ges.aprovamais.repository.ConviteRepository;
import tcc.ges.aprovamais.repository.CoordenadorRepository;
import tcc.ges.aprovamais.repository.OrientadorRepository;
import tcc.ges.aprovamais.repository.PreCadastroRepository;
import tcc.ges.aprovamais.repository.TurmaRepository;
import tcc.ges.aprovamais.repository.UsuarioRepository;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

// Essa service é quem cuida da lista de pré-cadastro mantida pelo coordenador
@Service
@RequiredArgsConstructor
public class PreCadastroService {

    public static final String SITUACAO_AGUARDANDO_CONVITE = "AGUARDANDO_CONVITE";
    public static final String SITUACAO_CONVITE_PENDENTE = "CONVITE_PENDENTE";
    public static final String SITUACAO_CADASTRO_CONCLUIDO = "CADASTRO_CONCLUIDO";

    private static final Logger log = LoggerFactory.getLogger(PreCadastroService.class);

    private final PreCadastroRepository preCadastroRepository;
    private final CoordenadorRepository coordenadorRepository;
    private final TurmaRepository turmaRepository;
    private final UsuarioRepository usuarioRepository;
    private final AlunoRepository alunoRepository;
    private final OrientadorRepository orientadorRepository;
    private final ConviteRepository conviteRepository;
    private final AuditoriaService auditoriaService;

    // Esse é o método que lista os pré-cadastros do coordenador logado
    @Transactional(readOnly = true)
    public List<PreCadastroResponse> listarDoCoordenador(String emailCoordenador) {
        return preCadastroRepository.findByCoordenadorEmailOrderByNomeAsc(emailCoordenador)
                .stream()
                .map(this::paraResponse)
                .toList();
    }

    /*
       Esse é o método que autoriza uma pessoa a receber convite. O e-mail e o
       identificador não podem repetir, e a turma do aluno tem que ser de um curso do próprio coordenador (RN21)
    */
    @Transactional
    public PreCadastroResponse cadastrar(String emailCoordenador, PreCadastroRequest request, String ipOrigem) {
        Coordenador coordenador = buscarCoordenador(emailCoordenador);
        String email = normalizarEmail(request.getEmail());
        String identificador = request.getIdentificadorInstitucional().trim();

        if (preCadastroRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalStateException("Este e-mail já está no pré-cadastro");
        }
        if (preCadastroRepository.existsByIdentificadorInstitucionalIgnoreCase(identificador)) {
            throw new IllegalStateException("Este RGM ou matrícula institucional já está no pré-cadastro");
        }
        validarContaInexistente(email);
        validarIdentificadorLivre(request.getPerfil(), identificador);

        PreCadastro preCadastro = new PreCadastro();
        preCadastro.setCoordenador(coordenador);
        preencher(preCadastro, request, email, identificador, emailCoordenador);
        PreCadastro salvo = preCadastroRepository.save(preCadastro);

        auditoriaService.registrar(coordenador, "PRE_CADASTRO_INCLUIDO",
                "Pré-cadastro id=" + salvo.getId() + " incluído (perfil " + salvo.getPerfil() + ")", ipOrigem, true);
        log.info("[PRE-CADASTRO] Pré-cadastro {} incluído pelo coordenador {}", salvo.getId(), emailCoordenador);

        return paraResponse(salvo);
    }

    /*
       Esse é o método que altera um pré-cadastro do coordenador logado. Depois que a pessoa concluiu o cadastro, os dados já estão na conta dela e não
       mudam mais por aqui. Com convite pendente, o e-mail não pode mudar, senão o link enviado deixaria de corresponder ao pré-cadastro
    */
    @Transactional
    public PreCadastroResponse atualizar(Long id, String emailCoordenador, PreCadastroRequest request, String ipOrigem) {
        PreCadastro preCadastro = preCadastroRepository.findByIdAndCoordenadorEmail(id, emailCoordenador)
                .orElseThrow(() -> new ResourceNotFoundException("Pré-cadastro não encontrado"));
        String email = normalizarEmail(request.getEmail());
        String identificador = request.getIdentificadorInstitucional().trim();

        if (SITUACAO_CADASTRO_CONCLUIDO.equals(situacao(preCadastro.getEmail()))) {
            throw new IllegalStateException("Esta pessoa já concluiu o cadastro, e o pré-cadastro não pode mais ser alterado");
        }
        boolean emailMudou = !email.equalsIgnoreCase(preCadastro.getEmail());
        if (emailMudou && conviteRepository.existsByEmailAndStatus(preCadastro.getEmail(), StatusConvite.PENDENTE)) {
            throw new IllegalStateException("Há um convite pendente para o e-mail atual; o e-mail não pode ser trocado");
        }
        if (preCadastroRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new IllegalStateException("Este e-mail já está no pré-cadastro");
        }
        if (preCadastroRepository.existsByIdentificadorInstitucionalIgnoreCaseAndIdNot(identificador, id)) {
            throw new IllegalStateException("Este RGM ou matrícula institucional já está no pré-cadastro");
        }
        if (emailMudou) {
            validarContaInexistente(email);
        }
        if (!identificador.equalsIgnoreCase(preCadastro.getIdentificadorInstitucional())
                || request.getPerfil() != preCadastro.getPerfil()) {
            validarIdentificadorLivre(request.getPerfil(), identificador);
        }

        preencher(preCadastro, request, email, identificador, emailCoordenador);
        PreCadastro salvo = preCadastroRepository.save(preCadastro);

        auditoriaService.registrar(salvo.getCoordenador(), "PRE_CADASTRO_ALTERADO",
                "Pré-cadastro id=" + id + " alterado", ipOrigem, true);
        log.info("[PRE-CADASTRO] Pré-cadastro {} alterado pelo coordenador {}", id, emailCoordenador);

        return paraResponse(salvo);
    }

    // Esse é usado pelo convite e pelo aceite para achar a pessoa autorizada
    @Transactional(readOnly = true)
    public Optional<PreCadastro> buscarPorEmail(String email) {
        if (email == null) {
            return Optional.empty();
        }
        return preCadastroRepository.findByEmailIgnoreCase(email.trim());
    }

    private void preencher(PreCadastro preCadastro, PreCadastroRequest request, String email,
                           String identificador, String emailCoordenador) {
        preCadastro.setNome(request.getNome().trim());
        preCadastro.setEmail(email);
        preCadastro.setPerfil(request.getPerfil());
        preCadastro.setIdentificadorInstitucional(identificador);

        // Turma só para aluno, e só de um curso do coordenador logado, de outro curso vira 404
        if (request.getPerfil() == PerfilDestino.ALUNO) {
            Turma turma = turmaRepository.findByIdAndCursoCoordenadorEmail(request.getTurmaId(), emailCoordenador)
                    .orElseThrow(() -> new ResourceNotFoundException("Turma não encontrada"));
            preCadastro.setTurma(turma);
        } else {
            preCadastro.setTurma(null);
        }
    }

    // Quem já tem conta ativa não precisa de convite
    private void validarContaInexistente(String email) {
        Optional<Usuario> usuario = usuarioRepository.findByEmail(email);
        if (usuario.isPresent() && !Boolean.TRUE.equals(usuario.get().getPrimeiroAcesso())) {
            throw new IllegalStateException("Já existe uma conta com este e-mail");
        }
    }

    // O RGM e a matrícula institucional são únicos nas tabelas de aluno e de orientador
    private void validarIdentificadorLivre(PerfilDestino perfil, String identificador) {
        boolean emUso = perfil == PerfilDestino.ALUNO
                ? alunoRepository.existsByRgm(identificador)
                : orientadorRepository.existsByMatriculaInstitucional(identificador);
        if (emUso) {
            throw new IllegalStateException("Este RGM ou matrícula institucional já pertence a uma conta");
        }
    }

    private String situacao(String email) {
        Optional<Usuario> usuario = usuarioRepository.findByEmail(email);
        if (usuario.isPresent() && !Boolean.TRUE.equals(usuario.get().getPrimeiroAcesso())) {
            return SITUACAO_CADASTRO_CONCLUIDO;
        }
        if (conviteRepository.existsByEmailAndStatus(email, StatusConvite.PENDENTE)) {
            return SITUACAO_CONVITE_PENDENTE;
        }
        return SITUACAO_AGUARDANDO_CONVITE;
    }

    private Coordenador buscarCoordenador(String email) {
        return coordenadorRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Coordenador não encontrado"));
    }

    // Email sempre minúsculo e sem espaços, para a comparação com o convite e com a conta não falhar
    static String normalizarEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private PreCadastroResponse paraResponse(PreCadastro preCadastro) {
        Turma turma = preCadastro.getTurma();
        return PreCadastroResponse.builder()
                .id(preCadastro.getId())
                .nome(preCadastro.getNome())
                .email(preCadastro.getEmail())
                .perfil(preCadastro.getPerfil().name())
                .identificadorInstitucional(preCadastro.getIdentificadorInstitucional())
                .turmaId(turma != null ? turma.getId() : null)
                .descricaoTurma(turma != null ? turma.getSemestre() + " - " + turma.getIdentificador() : null)
                .nomeCurso(turma != null ? turma.getCurso().getNome() : null)
                .situacao(situacao(preCadastro.getEmail()))
                .build();
    }
}
