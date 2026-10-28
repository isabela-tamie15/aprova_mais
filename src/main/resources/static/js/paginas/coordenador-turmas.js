// Script da página coordenador/turmas.html: lista, cadastra e altera as turmas de um curso

function turmasPagina() {
    return {
        cursos: [],
        cursoId: '',
        turmas: [],
        carregando: true,
        salvando: false,
        erro: '',
        sucesso: '',
        erros: {},
        form: { id: null, semestre: '', identificador: '', periodo: '', modalidade: '' },
        nomesPeriodo: NOMES_PERIODO,
        nomesModalidade: NOMES_MODALIDADE,

        // Carrega os cursos e já abre o primeiro, que é o caso mais comum (coordenador de um curso só)
        async init() {
            this.cursos = await carregarCursosDoCoordenador();
            if (this.cursos.length > 0) {
                this.cursoId = String(this.cursos[0].id);
                await this.carregarTurmas();
            }
            this.carregando = false;
        },

        // Ao trocar de curso, as mensagens da ação anterior deixam de valer
        async trocarCurso() {
            this.sucesso = '';
            await this.carregarTurmas();
        },

        async carregarTurmas() {
            this.cancelar();
            if (!this.cursoId) {
                this.turmas = [];
                return;
            }
            const r = await requisicaoJson('/api/v1/coordenador/turmas?cursoId=' + this.cursoId);
            if (r.ok) {
                this.turmas = r.dados;
            } else {
                this.erro = r.dados.mensagem || 'Erro ao carregar as turmas.';
            }
        },

        editar(turma) {
            this.form = {
                id: turma.id,
                semestre: turma.semestre,
                identificador: turma.identificador,
                periodo: turma.periodo,
                modalidade: turma.modalidade
            };
            this.erros = {};
            this.erro = '';
            this.sucesso = '';
            rolarParaFormulario(this.$refs.formulario);
        },

        cancelar() {
            this.form = { id: null, semestre: '', identificador: '', periodo: '', modalidade: '' };
            this.erros = {};
            this.erro = '';
        },

        // Mesmas regras do TurmaRequest. O backend valida de novo
        validar() {
            const erros = {};
            const identificador = this.form.identificador.trim();

            if (!/^\d{4}\/[12]$/.test(this.form.semestre.trim())) {
                erros.semestre = 'Informe o semestre no formato AAAA/1 ou AAAA/2.';
            }
            if (!identificador) {
                erros.identificador = 'Informe o identificador da turma.';
            } else if (identificador.length > 20) {
                erros.identificador = 'O identificador deve ter no máximo 20 caracteres.';
            }
            if (!this.form.periodo) {
                erros.periodo = 'Selecione o período.';
            }
            if (!this.form.modalidade) {
                erros.modalidade = 'Selecione a modalidade.';
            }

            this.erros = erros;
            return Object.keys(erros).length === 0;
        },

        async salvar() {
            this.erro = '';
            this.sucesso = '';
            if (!this.validar()) {
                return;
            }

            this.salvando = true;
            const editando = this.form.id !== null;
            const url = editando ? '/api/v1/coordenador/turmas/' + this.form.id : '/api/v1/coordenador/turmas';
            const r = await requisicaoJson(url, editando ? 'PUT' : 'POST', {
                cursoId: Number(this.cursoId),
                semestre: this.form.semestre.trim(),
                identificador: this.form.identificador.trim(),
                periodo: this.form.periodo,
                modalidade: this.form.modalidade
            });
            this.salvando = false;

            if (!r.ok) {
                if (r.dados.campos) {
                    this.erros = r.dados.campos;
                }
                this.erro = r.dados.mensagem || 'Erro ao salvar a turma.';
                return;
            }

            const mensagem = editando ? 'Turma alterada.' : 'Turma cadastrada.';
            await this.carregarTurmas();
            this.sucesso = mensagem;
        }
    }
}
