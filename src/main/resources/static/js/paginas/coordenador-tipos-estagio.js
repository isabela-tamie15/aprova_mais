// Script da página coordenador/tipos-estagio.html: lista, cadastra e altera os tipos de estágio de um curso

function tiposEstagioPagina() {
    return {
        cursos: [],
        cursoId: '',
        tipos: [],
        carregando: true,
        salvando: false,
        erro: '',
        sucesso: '',
        erros: {},
        form: { id: null, nome: '', descricao: '', cargaHorariaNecessaria: '', jornadaSemanal: '' },

        async init() {
            this.cursos = await carregarCursosDoCoordenador();
            if (this.cursos.length > 0) {
                this.cursoId = String(this.cursos[0].id);
                await this.carregarTipos();
            }
            this.carregando = false;
        },

        async trocarCurso() {
            this.sucesso = '';
            await this.carregarTipos();
        },

        async carregarTipos() {
            this.cancelar();
            const r = await requisicaoJson('/api/v1/coordenador/tipos-estagio?cursoId=' + this.cursoId);
            if (r.ok) {
                this.tipos = r.dados;
            } else {
                this.erro = r.dados.mensagem || 'Erro ao carregar os tipos de estágio.';
            }
        },

        // Quantas semanas o tipo leva para cumprir a carga, a mesma conta da CalculadoraCargaHoraria
        semanas(tipo) {
            const carga = Number(tipo.cargaHorariaNecessaria);
            const jornada = Number(tipo.jornadaSemanal);
            return jornada > 0 ? Math.ceil(carga / jornada) : '-';
        },

        editar(tipo) {
            this.form = {
                id: tipo.id,
                nome: tipo.nome,
                descricao: tipo.descricao || '',
                cargaHorariaNecessaria: String(Number(tipo.cargaHorariaNecessaria)),
                jornadaSemanal: String(Number(tipo.jornadaSemanal))
            };
            this.erros = {};
            this.erro = '';
            this.sucesso = '';
            rolarParaFormulario(this.$refs.formulario);
        },

        cancelar() {
            this.form = { id: null, nome: '', descricao: '', cargaHorariaNecessaria: '', jornadaSemanal: '' };
            this.erros = {};
            this.erro = '';
        },

        // Mesmas regras do TipoEstagioRequest. O backend valida de novo
        validar() {
            const erros = {};
            const nome = this.form.nome.trim();
            const carga = Number(this.form.cargaHorariaNecessaria);
            const jornada = Number(this.form.jornadaSemanal);

            if (!nome) {
                erros.nome = 'Informe o nome do tipo de estágio.';
            } else if (nome.length < 2 || nome.length > 100) {
                erros.nome = 'O nome deve ter entre 2 e 100 caracteres.';
            }
            if (this.form.descricao.length > 255) {
                erros.descricao = 'A descrição deve ter no máximo 255 caracteres.';
            }
            if (this.form.cargaHorariaNecessaria === '' || !(carga >= 1 && carga <= 2000)) {
                erros.cargaHorariaNecessaria = 'Informe uma carga entre 1 e 2000 horas.';
            }
            if (this.form.jornadaSemanal === '' || !(jornada >= 1 && jornada <= 44)) {
                erros.jornadaSemanal = 'Informe uma jornada entre 1 e 44 horas por semana.';
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
            const url = editando
                ? '/api/v1/coordenador/tipos-estagio/' + this.form.id
                : '/api/v1/coordenador/tipos-estagio';
            const r = await requisicaoJson(url, editando ? 'PUT' : 'POST', {
                cursoId: Number(this.cursoId),
                nome: this.form.nome.trim(),
                descricao: this.form.descricao.trim(),
                cargaHorariaNecessaria: Number(this.form.cargaHorariaNecessaria),
                jornadaSemanal: Number(this.form.jornadaSemanal)
            });
            this.salvando = false;

            if (!r.ok) {
                if (r.dados.campos) {
                    this.erros = r.dados.campos;
                }
                this.erro = r.dados.mensagem || 'Erro ao salvar o tipo de estágio.';
                return;
            }

            const mensagem = editando
                ? 'Tipo de estágio alterado. Os estágios já cadastrados mantêm a carga e a jornada antigas.'
                : 'Tipo de estágio cadastrado.';
            await this.carregarTipos();
            this.sucesso = mensagem;
        }
    }
}
