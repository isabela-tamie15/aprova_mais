// Página coordenador/estagios.html, com os estágios em aberto do curso e o encerramento por desligamento

const NOMES_STATUS_ESTAGIO = {
    PENDENTE: 'Pendente',
    ATIVO: 'Ativo',
    REJEITADO: 'Rejeitado',
    SUSPENSO: 'Suspenso'
};

const CLASSES_STATUS_ESTAGIO = {
    PENDENTE: 'bg-warning text-dark',
    ATIVO: 'bg-success',
    REJEITADO: 'bg-danger',
    SUSPENSO: 'bg-secondary'
};

function estagiosPagina() {
    return {
        cursos: [],
        cursoId: '',
        estagios: [],
        carregando: true,
        salvando: false,
        erro: '',
        sucesso: '',
        selecionado: null,
        observacao: '',
        erroObservacao: '',
        nomesStatus: NOMES_STATUS_ESTAGIO,
        classesStatus: CLASSES_STATUS_ESTAGIO,

        async init() {
            this.cursos = await carregarCursosDoCoordenador();
            if (this.cursos.length > 0) {
                this.cursoId = String(this.cursos[0].id);
                await this.carregarEstagios();
            }
            this.carregando = false;
        },

        async trocarCurso() {
            this.sucesso = '';
            await this.carregarEstagios();
        },

        async carregarEstagios() {
            this.cancelar();
            this.erro = '';
            const resposta = await requisicaoJson('/api/v1/coordenador/estagios?cursoId=' + this.cursoId);
            if (resposta.ok) {
                this.estagios = resposta.dados;
            } else {
                this.estagios = [];
                this.erro = resposta.dados.mensagem || 'Erro ao carregar os estágios.';
            }
        },

        abrirDesligamento(estagio) {
            this.selecionado = estagio;
            this.observacao = '';
            this.erroObservacao = '';
            this.sucesso = '';
            this.$nextTick(() => rolarParaFormulario(this.$refs.formulario));
        },

        cancelar() {
            this.selecionado = null;
            this.observacao = '';
            this.erroObservacao = '';
        },

        async desligar() {
            this.erroObservacao = '';
            if (!this.observacao.trim()) {
                this.erroObservacao = 'Informe a observação do encerramento.';
                return;
            }
            this.salvando = true;
            const nomeAluno = this.selecionado.nomeAluno;
            const resposta = await requisicaoJson(`/api/v1/coordenador/estagios/${this.selecionado.id}/desligar`,
                'POST', { observacao: this.observacao.trim() });
            this.salvando = false;
            if (resposta.ok) {
                await this.carregarEstagios();
                this.sucesso = 'Estágio de ' + nomeAluno + ' encerrado por desligamento.';
            } else if (resposta.dados.campos && resposta.dados.campos.observacao) {
                this.erroObservacao = resposta.dados.campos.observacao;
            } else {
                this.erro = resposta.dados.mensagem || 'Não foi possível encerrar o estágio.';
            }
        }
    };
}
