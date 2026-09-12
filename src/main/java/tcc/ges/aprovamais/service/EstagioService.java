package tcc.ges.aprovamais.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tcc.ges.aprovamais.dto.EstagioCadastroRequest;
import tcc.ges.aprovamais.dto.EstagioResponse;
import tcc.ges.aprovamais.dto.TipoEstagioResponse;
import tcc.ges.aprovamais.entity.Aluno;
import tcc.ges.aprovamais.entity.Estagio;
import tcc.ges.aprovamais.entity.Matricula;
import tcc.ges.aprovamais.entity.Orientador;
import tcc.ges.aprovamais.entity.TipoEstagio;
import tcc.ges.aprovamais.entity.Usuario;
import tcc.ges.aprovamais.entity.enums.StatusEstagio;
import tcc.ges.aprovamais.entity.enums.StatusMatricula;
import tcc.ges.aprovamais.exception.ResourceNotFoundException;
import tcc.ges.aprovamais.repository.AlunoRepository;
import tcc.ges.aprovamais.repository.EstagioRepository;
import tcc.ges.aprovamais.repository.MatriculaRepository;
import tcc.ges.aprovamais.repository.TipoEstagioRepository;
import tcc.ges.aprovamais.repository.UsuarioRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EstagioService {

    private final EstagioRepository estagioRepository;
    private final MatriculaRepository matriculaRepository;
    private final TipoEstagioRepository tipoEstagioRepository;
    private final AlunoRepository alunoRepository;
    private final UsuarioRepository usuarioRepository;

    public List<TipoEstagioResponse> listarTiposEstagioDisponiveis() {
        List<TipoEstagio> tipos = tipoEstagioRepository.findAll();

        List<TipoEstagioResponse> respostas = new ArrayList<>();
        for (TipoEstagio tipo : tipos) {
            TipoEstagioResponse resposta = new TipoEstagioResponse();
            resposta.setId(tipo.getId());
            resposta.setNome(tipo.getNome());
            resposta.setCargaHorariaSemanal(tipo.getCargaHorariaNecessaria());
            respostas.add(resposta);
        }
        return respostas;
    }

    public Optional<EstagioResponse> buscarEstagioDoAluno(String emailAluno) {
        Aluno aluno = buscarAlunoPorEmail(emailAluno);
        Matricula matricula = buscarMatriculaAtiva(aluno);

        Optional<Estagio> estagioEncontrado = estagioRepository.findByMatriculaId(matricula.getId());

        if (estagioEncontrado.isPresent()) {
            Estagio estagio = estagioEncontrado.get();
            EstagioResponse resposta = paraResponse(estagio);
            return Optional.of(resposta);
        } else {
            return Optional.empty();
        }
    }

    @Transactional
    public EstagioResponse cadastrarOuAtualizarEstagio(String emailAluno, EstagioCadastroRequest request) {
        Aluno aluno = buscarAlunoPorEmail(emailAluno);
        Matricula matricula = buscarMatriculaAtiva(aluno);

        TipoEstagio tipoEstagio = tipoEstagioRepository.findById(request.getTipoEstagioId())
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de estágio não encontrado"));

        Optional<Estagio> estagioExistente = estagioRepository.findByMatriculaId(matricula.getId());

        Estagio estagio;
        if (estagioExistente.isPresent()) {
            estagio = estagioExistente.get();

            if (estagio.getStatus() == StatusEstagio.PENDENTE) {
                throw new IllegalStateException(
                        "Já existe uma solicitação aguardando aprovação do orientador");
            }
        } else {
            estagio = new Estagio();
            estagio.setMatricula(matricula);
        }

        estagio.setTipoEstagio(tipoEstagio);
        estagio.setDataInicio(request.getDataInicio());
        estagio.setCargaHorariaNecessaria(tipoEstagio.getCargaHorariaNecessaria());
        estagio.setStatus(StatusEstagio.PENDENTE);
        estagio.setJustificativaRejeicao(null);

        Estagio salvo = estagioRepository.save(estagio);
        return paraResponse(salvo);
    }

    public List<EstagioResponse> listarPendentes() {
        List<Estagio> estagiosPendentes = estagioRepository.findByStatus(StatusEstagio.PENDENTE);

        List<EstagioResponse> respostas = new ArrayList<>();
        for (Estagio estagio : estagiosPendentes) {
            EstagioResponse resposta = paraResponse(estagio);
            respostas.add(resposta);
        }
        return respostas;
    }

    @Transactional
    public EstagioResponse aprovar(Long estagioId, String emailOrientador) {
        Estagio estagio = buscarEstagioPendente(estagioId);
        Orientador orientador = buscarOrientadorPorEmail(emailOrientador);

        estagio.setOrientador(orientador);
        estagio.setStatus(StatusEstagio.ATIVO);
        estagio.setJustificativaRejeicao(null);

        return paraResponse(estagioRepository.save(estagio));
    }

    @Transactional
    public EstagioResponse rejeitar(Long estagioId, String emailOrientador, String justificativa) {
        Estagio estagio = buscarEstagioPendente(estagioId);
        Orientador orientador = buscarOrientadorPorEmail(emailOrientador);

        estagio.setOrientador(orientador);
        estagio.setStatus(StatusEstagio.REJEITADO);
        estagio.setJustificativaRejeicao(justificativa);

        return paraResponse(estagioRepository.save(estagio));
    }

    private Aluno buscarAlunoPorEmail(String email) {
        return alunoRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Aluno não encontrado"));
    }

    private Orientador buscarOrientadorPorEmail(String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Orientador não encontrado"));
        return (Orientador) usuario;
    }

    private Matricula buscarMatriculaAtiva(Aluno aluno) {
        return matriculaRepository.findFirstByAlunoIdAndStatus(aluno.getId(), StatusMatricula.ATIVA)
                .orElseThrow(() -> new ResourceNotFoundException("Aluno não possui matrícula ativa"));
    }

    private Estagio buscarEstagioPendente(Long id) {
        Estagio estagio = estagioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Estágio não encontrado"));

        if (estagio.getStatus() != StatusEstagio.PENDENTE) {
            throw new IllegalStateException("Este estágio já foi analisado");
        }
        return estagio;
    }

    private EstagioResponse paraResponse(Estagio estagio) {
        EstagioResponse resposta = new EstagioResponse();

        resposta.setId(estagio.getId());
        resposta.setStatus(estagio.getStatus().name());
        resposta.setDataInicio(estagio.getDataInicio());
        resposta.setNomeTipoEstagio(estagio.getTipoEstagio().getNome());
        resposta.setCargaHorariaSemanal(estagio.getCargaHorariaNecessaria());
        resposta.setNomeAluno(estagio.getMatricula().getAluno().getNome());
        resposta.setJustificativaRejeicao(estagio.getJustificativaRejeicao());

        if (estagio.getOrientador() != null) {
            resposta.setNomeOrientador(estagio.getOrientador().getNome());
        } else {
            resposta.setNomeOrientador(null);
        }

        return resposta;
    }
}