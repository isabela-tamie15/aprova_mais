// Página coordenador/pre-cadastros.html, que lista, inclui e altera o pré-cadastro

function formularioVazio(cursoId) {
    return { id: null, nome: '', email: '', perfil: '', identificadorInstitucional: '', cursoId: cursoId, turmaId: '' };
}

function preCadastrosPagina() {
    return {
        cursos: [],
        turmasPorCurso: {},
        preCadastros: [],
        carregando: true,
        salvando: false,
        erro: '',
        sucesso: '',
        erros: {},
        form: formularioVazio(''),
        nomesSituacao: {
            AGUARDANDO_CONVITE: 'Aguardando convite',
            CONVITE_PENDENTE: 'Convite enviado',
            CADASTRO_CONCLUIDO: 'Cadastro concluído'
        },
        classesSituacao: {
            AGUARDANDO_CONVITE: 'bg-secondary',
            CONVITE_PENDENTE: 'bg-primary',
            CADASTRO_CONCLUIDO: 'bg-success'
        },

        // Chamado pelo Alpine ao abrir a tela. Carrega os cursos, as turmas de cada curso e a lista
        async init() {
            this.cursos = await carregarCursosDoCoordenador();
            for (const curso of this.cursos) {
                const resposta = await requisicaoJson('/api/v1/coordenador/turmas?cursoId=' + curso.id);
                this.turmasPorCurso[curso.id] = resposta.ok ? resposta.dados : [];
            }
            this.form = formularioVazio(this.primeiroCurso());
            await this.carregarLista();
            this.carregando = false;
        },

        primeiroCurso() {
            return this.cursos.length > 0 ? String(this.cursos[0].id) : '';
        },

        async carregarLista() {
            const resposta = await requisicaoJson('/api/v1/coordenador/pre-cadastros');
            if (resposta.ok) {
                this.preCadastros = resposta.dados;
            } else {
                this.erro = resposta.dados.mensagem || 'Erro ao carregar o pré-cadastro.';
            }
        },

        // O curso não vem na resposta, então é achado pela turma
        cursoDaTurma(turmaId) {
            for (const [cursoId, turmas] of Object.entries(this.turmasPorCurso)) {
                // Se alguma turma deste curso tem o id procurado, achamos o curso
                for (const turma of turmas) {
                    if (turma.id === turmaId) {
                        return cursoId;
                    }
                }
            }
            return this.primeiroCurso();
        },

        editar(item) {
            this.form = {
                id: item.id,
                nome: item.nome,
                email: item.email,
                perfil: item.perfil,
                identificadorInstitucional: item.identificadorInstitucional,
                cursoId: item.turmaId ? this.cursoDaTurma(item.turmaId) : this.primeiroCurso(),
                turmaId: item.turmaId ? String(item.turmaId) : ''
            };
            this.erros = {};
            this.erro = '';
            this.sucesso = '';
            rolarParaFormulario(this.$refs.formulario);
        },

        cancelar() {
            this.form = formularioVazio(this.primeiroCurso());
            this.erros = {};
            this.erro = '';
        },

        // Mesmas regras do PreCadastroRequest. O backend valida de novo
        validar() {
            const erros = {};
            const nome = this.form.nome.trim();
            const identificador = this.form.identificadorInstitucional.trim();

            if (nome.length < 3) {
                erros.nome = 'Informe o nome completo.';
            }
            if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.form.email.trim())) {
                erros.email = 'Informe um e-mail válido.';
            }
            if (!this.form.perfil) {
                erros.perfil = 'Escolha o perfil.';
            }
            if (!identificador) {
                erros.identificadorInstitucional = 'Informe o RGM ou a matrícula institucional.';
            } else if (!/^[0-9A-Za-z.-]+$/.test(identificador)) {
                erros.identificadorInstitucional = 'Use apenas letras, números, ponto e hífen.';
            }
            // Aluno sem turma é recusado
            if (this.form.perfil === 'ALUNO' && !this.form.turmaId) {
                erros.turmaId = 'Escolha a turma do aluno.';
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
                ? '/api/v1/coordenador/pre-cadastros/' + this.form.id
                : '/api/v1/coordenador/pre-cadastros';
            const resposta = await requisicaoJson(url, editando ? 'PUT' : 'POST', {
                nome: this.form.nome.trim(),
                email: this.form.email.trim(),
                perfil: this.form.perfil,
                identificadorInstitucional: this.form.identificadorInstitucional.trim(),
                turmaId: this.form.perfil === 'ALUNO' ? Number(this.form.turmaId) : null
            });
            this.salvando = false;

            if (!resposta.ok) {
                if (resposta.dados.campos) {
                    this.erros = resposta.dados.campos;
                }
                this.erro = resposta.dados.mensagem || 'Erro ao salvar o pré-cadastro.';
                return;
            }

            const mensagem = editando ? 'Pré-cadastro alterado.' : 'Pessoa incluída na lista.';
            this.cancelar();
            await this.carregarLista();
            this.sucesso = mensagem;
        }
    };
}
