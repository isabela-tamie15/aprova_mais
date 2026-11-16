// Página coordenador/turmas.html, que lista, cadastra e altera as turmas de um curso e define o orientador de cada uma

function turmasPagina() {
    return {
        cursos: [],
        cursoId: '',
        turmas: [],
        orientadores: [],
        atribuindo: null,
        carregando: true,
        salvando: false,
        erro: '',
        sucesso: '',
        erros: {},
        form: { id: null, semestre: '', identificador: '', periodo: '', modalidade: '', dataEncerramentoEntregas: '' },
        nomesPeriodo: NOMES_PERIODO,
        nomesModalidade: NOMES_MODALIDADE,

        // Carrega os cursos e já abre o primeiro, que é o caso mais comum (coordenador de um curso só)
        async init() {
            this.cursos = await carregarCursosDoCoordenador();
            const respostaOrientadores = await requisicaoJson('/api/v1/coordenador/turmas/orientadores');
            this.orientadores = respostaOrientadores.ok ? respostaOrientadores.dados : [];
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
            const resposta = await requisicaoJson('/api/v1/coordenador/turmas?cursoId=' + this.cursoId);
            if (resposta.ok) {
                this.turmas = resposta.dados;
            } else {
                this.erro = resposta.dados.mensagem || 'Erro ao carregar as turmas.';
            }
        },

        editar(turma) {
            this.form = {
                id: turma.id,
                semestre: turma.semestre,
                identificador: turma.identificador,
                periodo: turma.periodo,
                modalidade: turma.modalidade,
                // Turmas antigas vêm sem a data, então o campo começa vazio para o coordenador preencher
                dataEncerramentoEntregas: turma.dataEncerramentoEntregas || ''
            };
            this.erros = {};
            this.erro = '';
            this.sucesso = '';
            rolarParaFormulario(this.$refs.formulario);
        },

        cancelar() {
            this.form = { id: null, semestre: '', identificador: '', periodo: '', modalidade: '', dataEncerramentoEntregas: '' };
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
            if (!this.form.dataEncerramentoEntregas) {
                erros.dataEncerramentoEntregas = 'Informe a data de encerramento das entregas do semestre.';
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
            const resposta = await requisicaoJson(url, editando ? 'PUT' : 'POST', {
                cursoId: Number(this.cursoId),
                semestre: this.form.semestre.trim(),
                identificador: this.form.identificador.trim(),
                periodo: this.form.periodo,
                modalidade: this.form.modalidade,
                dataEncerramentoEntregas: this.form.dataEncerramentoEntregas
            });
            this.salvando = false;

            if (!resposta.ok) {
                if (resposta.dados.campos) {
                    this.erros = resposta.dados.campos;
                }
                this.erro = resposta.dados.mensagem || 'Erro ao salvar a turma.';
                return;
            }

            const mensagem = editando ? 'Turma alterada.' : 'Turma cadastrada.';
            await this.carregarTurmas();
            this.sucesso = mensagem;
        },

        /*
           Atribui ou troca o orientador da turma. Os estágios em aberto da turma passam para o novo
           orientador, então a troca pede confirmação. Se a pessoa desistir ou der erro, o select volta
           ao orientador anterior
        */
        async atribuirOrientador(turma, select) {
            this.erro = '';
            this.sucesso = '';
            const orientadorId = Number(select.value);
            const escolhido = this.orientadores.find(o => o.id === orientadorId);
            if (!escolhido) {
                select.value = turma.orientadorId ? String(turma.orientadorId) : '';
                return;
            }
            if (turma.orientadorId) {
                const confirmou = await confirmarAcao('Trocar o orientador da turma ' + turma.identificador + ' para '
                    + escolhido.nome + '? Os estágios em aberto da turma passam para o novo orientador.', 'Trocar orientador');
                if (!confirmou) {
                    select.value = String(turma.orientadorId);
                    return;
                }
            }

            this.atribuindo = turma.id;
            const resposta = await requisicaoJson('/api/v1/coordenador/turmas/' + turma.id + '/orientador', 'PUT',
                { orientadorId: orientadorId });
            this.atribuindo = null;

            if (!resposta.ok) {
                select.value = turma.orientadorId ? String(turma.orientadorId) : '';
                this.erro = resposta.dados.mensagem || 'Erro ao atribuir o orientador.';
                return;
            }
            const indice = this.turmas.findIndex(t => t.id === turma.id);
            this.turmas.splice(indice, 1, resposta.dados);
            this.sucesso = escolhido.nome + ' agora orienta a turma ' + turma.identificador + '.';
        },

        // A API devolve a data no formato 2026-06-15, e na tabela mostramos no formato 15/06/2026
        formatarData(data) {
            if (!data) {
                return 'Não informada';
            }
            return data.split('-').reverse().join('/');
        }
    }
}
