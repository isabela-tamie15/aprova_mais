// Página coordenador/dashboard.html, que carrega os números do dashboard do coordenador

// Painel vazio, usado antes da primeira resposta da API e quando dá erro, para a tela nunca ler um número que não chegou
function painelVazio() {
    return {
        estagiosSemOrientador: 0,
        estagiosAguardandoValidacao: 0,
        estagiosEmAndamento: 0,
        turmasSemDataEncerramento: 0,
        tarefasAguardandoCorrecao: 0,
        estagiosRetaFinal: 0,
        retaFinalSoTarefas: 0,
        retaFinalSoHoras: 0,
        retaFinalDuasPendencias: 0,
        estagiosConcluidos: 0,
        opcoesFiltro: { semestres: [], turmas: [], orientadores: [] },
        situacaoPorTurma: [],
        correcaoPorTurma: [],
        prazoPorTurma: []
    };
}

// Nomes dos filtros na barra de endereço, para o filtro continuar depois de atualizar a página
const FILTROS_DO_PAINEL = ['cursoId', 'semestre', 'turmaId', 'orientadorId'];

// A tela não faz contas. Os números, a largura das barras e o texto do prazo vêm prontos do PainelCoordenadorService
function painelPagina() {
    return {
        cursos: [],
        filtros: { cursoId: '', semestre: '', turmaId: '', orientadorId: '' },
        opcoes: { semestres: [], turmas: [], orientadores: [] },
        painel: painelVazio(),
        carregando: true,
        erro: '',

        async init() {
            const parametros = new URLSearchParams(window.location.search);
            for (const nome of FILTROS_DO_PAINEL) {
                this.filtros[nome] = parametros.get(nome) || '';
            }
            this.cursos = await carregarCursosDoCoordenador();
            await this.carregarPainel();
        },

        // Turmas do seletor, só do curso e do semestre escolhidos
        turmasDisponiveis() {
            return this.opcoes.turmas.filter(turma =>
                (!this.filtros.cursoId || String(turma.cursoId) === String(this.filtros.cursoId)) &&
                (!this.filtros.semestre || turma.semestre === this.filtros.semestre));
        },

        // Ao trocar o curso ou o semestre, a turma escolhida sai se não fizer mais parte da lista
        trocarCurso() {
            this.descartarTurmaForaDaLista();
            this.carregarPainel();
        },

        trocarSemestre() {
            this.descartarTurmaForaDaLista();
            this.carregarPainel();
        },

        descartarTurmaForaDaLista() {
            const turmaId = String(this.filtros.turmaId);
            if (turmaId && !this.turmasDisponiveis().some(turma => String(turma.id) === turmaId)) {
                this.filtros.turmaId = '';
            }
        },

        // Com um curso escolhido, o nome do curso sai da opção da turma
        nomeDaTurma(turma) {
            return this.filtros.cursoId ? turma.turma + ' · ' + turma.semestre : turma.nome;
        },

        temFiltro() {
            return FILTROS_DO_PAINEL.some(nome => this.filtros[nome]);
        },

        limparFiltros() {
            this.filtros = { cursoId: '', semestre: '', turmaId: '', orientadorId: '' };
            this.carregarPainel();
        },

        // Frase com os filtros escolhidos, como "Engenharia de Software, 2026/2, turma 8B"
        descricaoFiltros() {
            const partes = [];
            const curso = this.cursos.find(item => String(item.id) === String(this.filtros.cursoId));
            partes.push(curso ? curso.nome : 'todos os seus cursos');
            if (this.filtros.semestre) {
                partes.push('semestre ' + this.filtros.semestre);
            }
            const turma = this.opcoes.turmas.find(item => String(item.id) === String(this.filtros.turmaId));
            if (turma) {
                partes.push('turma ' + turma.turma);
            }
            const orientador = this.opcoes.orientadores.find(item => String(item.id) === String(this.filtros.orientadorId));
            if (orientador) {
                partes.push('orientador ' + orientador.nome);
            }
            return partes.join(', ');
        },

        totalAcompanhados() {
            return this.painel.estagiosSemOrientador + this.painel.estagiosAguardandoValidacao + this.painel.estagiosEmAndamento;
        },

        // Chamado ao abrir a página e a cada troca de filtro. Os filtros vão na URL da API e na barra de endereço
        async carregarPainel() {
            this.carregando = true;
            this.erro = '';
            const parametros = new URLSearchParams();
            for (const nome of FILTROS_DO_PAINEL) {
                if (this.filtros[nome]) {
                    parametros.set(nome, this.filtros[nome]);
                }
            }
            const consulta = parametros.toString();
            window.history.replaceState(null, '', window.location.pathname + (consulta ? '?' + consulta : ''));

            const resposta = await requisicaoJson('/api/v1/coordenador/painel' + (consulta ? '?' + consulta : ''));
            this.carregando = false;
            if (resposta.ok) {
                this.painel = resposta.dados;
                this.opcoes = resposta.dados.opcoesFiltro;
            } else {
                this.painel = painelVazio();
                this.erro = (resposta.dados && resposta.dados.mensagem) || 'Erro ao carregar o painel.';
            }
        }
    };
}
