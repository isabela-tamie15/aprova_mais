// Script da página coordenador/trilhas.html: monta a trilha de cada tipo de estágio e as tarefas dela

function trilhasPagina() {
    return {
        cursos: [],
        cursoId: '',
        tipos: [],
        tipoId: '',
        trilha: null,
        carregando: true,
        salvando: false,
        editandoTrilha: false,
        erro: '',
        sucesso: '',
        erros: {},
        formTrilha: { nome: '', descricao: '' },
        formTarefa: { id: null, nome: '', descricao: '', ordem: '', linkApoio: '' },

        async init() {
            this.cursos = await carregarCursosDoCoordenador();
            if (this.cursos.length > 0) {
                this.cursoId = String(this.cursos[0].id);
                await this.carregarTipos();
            }
            this.carregando = false;
        },

        limparMensagens() {
            this.erro = '';
            this.sucesso = '';
            this.erros = {};
        },

        async trocarCurso() {
            this.limparMensagens();
            await this.carregarTipos();
        },

        async carregarTipos() {
            this.tipos = [];
            this.tipoId = '';
            this.trilha = null;
            const r = await requisicaoJson('/api/v1/coordenador/tipos-estagio?cursoId=' + this.cursoId);
            if (!r.ok) {
                this.erro = r.dados.mensagem || 'Erro ao carregar os tipos de estágio.';
                return;
            }
            this.tipos = r.dados;
            if (this.tipos.length > 0) {
                this.tipoId = String(this.tipos[0].id);
                await this.carregarTrilha();
            }
        },

        async trocarTipo() {
            this.limparMensagens();
            await this.carregarTrilha();
        },

        // 204 quer dizer que o tipo ainda não tem trilha: a tela mostra o formulário de criação
        async carregarTrilha() {
            this.editandoTrilha = false;
            this.cancelarTarefa();
            const r = await requisicaoJson('/api/v1/coordenador/trilhas?tipoEstagioId=' + this.tipoId);
            if (r.status === 204) {
                this.trilha = null;
                this.formTrilha = { nome: '', descricao: '' };
            } else if (r.ok) {
                this.trilha = r.dados;
                this.formTarefa.ordem = String(this.proximaOrdem());
            } else {
                this.erro = r.dados.mensagem || 'Erro ao carregar a trilha.';
            }
        },

        // Sugere a próxima posição livre depois da última tarefa
        proximaOrdem() {
            if (!this.trilha || this.trilha.tarefas.length === 0) {
                return 1;
            }
            return Math.max(...this.trilha.tarefas.map(t => t.ordem)) + 1;
        },

        // ---------- trilha ----------

        validarTrilha() {
            const erros = {};
            const nome = this.formTrilha.nome.trim();
            if (!nome) {
                erros.nome = 'Informe o nome da trilha.';
            } else if (nome.length < 2 || nome.length > 100) {
                erros.nome = 'O nome da trilha deve ter entre 2 e 100 caracteres.';
            }
            if (this.formTrilha.descricao.length > 255) {
                erros.descricao = 'A descrição deve ter no máximo 255 caracteres.';
            }
            this.erros = erros;
            return Object.keys(erros).length === 0;
        },

        editarTrilha() {
            this.limparMensagens();
            this.formTrilha = { nome: this.trilha.nome, descricao: this.trilha.descricao || '' };
            this.editandoTrilha = true;
        },

        async salvarTrilha() {
            this.limparMensagens();
            if (!this.validarTrilha()) {
                return;
            }
            this.salvando = true;
            const editando = this.trilha !== null;
            const url = editando ? '/api/v1/coordenador/trilhas/' + this.trilha.id : '/api/v1/coordenador/trilhas';
            const r = await requisicaoJson(url, editando ? 'PUT' : 'POST', {
                tipoEstagioId: Number(this.tipoId),
                nome: this.formTrilha.nome.trim(),
                descricao: this.formTrilha.descricao.trim()
            });
            this.salvando = false;

            if (!r.ok) {
                this.mostrarErro(r, 'Erro ao salvar a trilha.');
                return;
            }
            this.trilha = r.dados;
            this.editandoTrilha = false;
            this.formTarefa.ordem = String(this.proximaOrdem());
            this.sucesso = editando ? 'Trilha alterada.' : 'Trilha criada. Agora adicione as tarefas.';
        },

        // ---------- tarefas ----------

        validarTarefa() {
            const erros = {};
            const nome = this.formTarefa.nome.trim();
            const ordem = Number(this.formTarefa.ordem);
            const link = this.formTarefa.linkApoio.trim();

            if (!nome) {
                erros.nome = 'Informe o nome da tarefa.';
            } else if (nome.length < 2 || nome.length > 100) {
                erros.nome = 'O nome da tarefa deve ter entre 2 e 100 caracteres.';
            }
            if (this.formTarefa.descricao.length > 255) {
                erros.descricao = 'A descrição deve ter no máximo 255 caracteres.';
            }
            if (!Number.isInteger(ordem) || ordem < 1 || ordem > 99) {
                erros.ordem = 'Informe uma ordem entre 1 e 99.';
            }
            if (link && !/^https?:\/\/\S+$/.test(link)) {
                erros.linkApoio = 'O link deve começar com http:// ou https://.';
            }
            this.erros = erros;
            return Object.keys(erros).length === 0;
        },

        editarTarefa(tarefa) {
            this.limparMensagens();
            this.formTarefa = {
                id: tarefa.id,
                nome: tarefa.nome,
                descricao: tarefa.descricao || '',
                ordem: String(tarefa.ordem),
                linkApoio: tarefa.linkApoio || ''
            };
            rolarParaFormulario(this.$refs.formularioTarefa);
        },

        cancelarTarefa() {
            this.formTarefa = { id: null, nome: '', descricao: '', ordem: String(this.proximaOrdem()), linkApoio: '' };
            this.erros = {};
        },

        async salvarTarefa() {
            this.limparMensagens();
            if (!this.validarTarefa()) {
                return;
            }
            this.salvando = true;
            const editando = this.formTarefa.id !== null;
            const url = editando
                ? '/api/v1/coordenador/trilhas/tarefas/' + this.formTarefa.id
                : '/api/v1/coordenador/trilhas/' + this.trilha.id + '/tarefas';
            const r = await requisicaoJson(url, editando ? 'PUT' : 'POST', {
                nome: this.formTarefa.nome.trim(),
                descricao: this.formTarefa.descricao.trim(),
                ordem: Number(this.formTarefa.ordem),
                linkApoio: this.formTarefa.linkApoio.trim()
            });
            this.salvando = false;

            if (!r.ok) {
                this.mostrarErro(r, 'Erro ao salvar a tarefa.');
                return;
            }
            this.trilha = r.dados;
            this.cancelarTarefa();
            this.sucesso = editando ? 'Tarefa alterada.' : 'Tarefa adicionada.';
        },

        async removerTarefa(tarefa) {
            if (!confirm('Remover a tarefa "' + tarefa.nome + '" da trilha?')) {
                return;
            }
            this.limparMensagens();
            const r = await requisicaoJson('/api/v1/coordenador/trilhas/tarefas/' + tarefa.id, 'DELETE');
            if (!r.ok) {
                this.mostrarErro(r, 'Erro ao remover a tarefa.');
                return;
            }
            await this.carregarTrilha();
            this.sucesso = 'Tarefa removida.';
        },

        mostrarErro(r, padrao) {
            if (r.dados && r.dados.campos) {
                this.erros = r.dados.campos;
            }
            this.erro = (r.dados && r.dados.mensagem) || padrao;
        }
    }
}
