// Script da página coordenador/cursos.html: lista, cadastra e altera os cursos do coordenador

function cursosPagina() {
    return {
        cursos: [],
        carregando: true,
        salvando: false,
        erro: '',
        sucesso: '',
        erros: {},
        form: { id: null, nome: '', codigoMec: '' },

        async init() {
            await this.carregar();
        },

        async carregar() {
            this.carregando = true;
            const r = await requisicaoJson('/api/v1/coordenador/cursos');
            this.carregando = false;
            if (r.ok) {
                this.cursos = r.dados;
            } else {
                this.erro = r.dados.mensagem || 'Erro ao carregar os cursos.';
            }
        },

        // Coloca o curso escolhido no formulário, que passa a funcionar como edição
        editar(curso) {
            this.form = { id: curso.id, nome: curso.nome, codigoMec: curso.codigoMec };
            this.erros = {};
            this.erro = '';
            this.sucesso = '';
            rolarParaFormulario(this.$refs.formulario);
        },

        cancelar() {
            this.form = { id: null, nome: '', codigoMec: '' };
            this.erros = {};
            this.erro = '';
        },

        /*
           Validação na tela, com as mesmas regras do CursoRequest. O backend
           valida de novo, porque essa parte pode ser contornada chamando a API direto
        */
        validar() {
            const erros = {};
            const nome = this.form.nome.trim();
            const codigo = this.form.codigoMec.trim();

            if (!nome) {
                erros.nome = 'Informe o nome do curso.';
            } else if (nome.length < 3 || nome.length > 150) {
                erros.nome = 'O nome do curso deve ter entre 3 e 150 caracteres.';
            }
            if (!codigo) {
                erros.codigoMec = 'Informe o código e-MEC do curso.';
            } else if (!/^[0-9A-Za-z-]{1,20}$/.test(codigo)) {
                erros.codigoMec = 'Use até 20 letras, números ou hífen.';
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
            const url = editando ? '/api/v1/coordenador/cursos/' + this.form.id : '/api/v1/coordenador/cursos';
            const r = await requisicaoJson(url, editando ? 'PUT' : 'POST', {
                nome: this.form.nome.trim(),
                codigoMec: this.form.codigoMec.trim()
            });
            this.salvando = false;

            if (!r.ok) {
                if (r.dados.campos) {
                    this.erros = r.dados.campos;
                }
                this.erro = r.dados.mensagem || 'Erro ao salvar o curso.';
                return;
            }

            this.sucesso = editando ? 'Curso alterado.' : 'Curso cadastrado.';
            this.cancelar();
            await this.carregar();
        }
    }
}
