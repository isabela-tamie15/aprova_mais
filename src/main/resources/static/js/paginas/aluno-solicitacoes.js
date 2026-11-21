// Página aluno/solicitacoes.html, com os pedidos do aluno e o formulário de um pedido novo

function solicitacoesAluno() {
    return {
        estagio: null,
        solicitacoes: [],
        tiposDeEstagio: [],
        prazoReabertura: null,
        carregando: true,
        salvando: false,
        erro: '',
        sucesso: '',
        erros: {},
        cancelandoId: null,
        form: { tipo: '', novoTipoEstagioId: '', motivo: '' },
        nomeTipo: NOMES_TIPO_SOLICITACAO,
        nomeStatus: nomeStatusSolicitacao,
        classeStatus: classeStatusSolicitacao,
        formatarData: formatarDataSolicitacao,
        formatarDataHora: formatarDataHoraSolicitacao,

        async init() {
            await this.carregar();
            this.carregando = false;
        },

        async carregar() {
            const [estagio, solicitacoes, tipos] = await Promise.all([
                chamarApiSolicitacoes('/api/v1/aluno/estagio'),
                chamarApiSolicitacoes('/api/v1/aluno/solicitacoes'),
                chamarApiSolicitacoes('/api/v1/aluno/estagio/tipos')
            ]);
            this.estagio = estagio.ok ? estagio.dados : null;
            if (solicitacoes.ok) {
                this.solicitacoes = solicitacoes.dados;
            } else {
                this.erro = solicitacoes.dados.mensagem || 'Erro ao carregar as solicitações.';
            }
            // Na troca, o tipo atual não aparece como opção
            const tipoAtual = this.estagio ? this.estagio.nomeTipoEstagio : null;
            this.tiposDeEstagio = tipos.ok ? tipos.dados.filter(tipo => tipo.nome !== tipoAtual) : [];

            // O prazo da reabertura vem do último trancamento aprovado
            const trancamento = this.solicitacoes.find(s => s.tipo === 'TRANCAMENTO' && s.status === 'APROVADA');
            this.prazoReabertura = trancamento ? trancamento.prazoArrependimento : null;
        },

        // Com o estágio ativo, o aluno pede a troca ou o trancamento. Com o estágio suspenso, a reabertura dentro do prazo
        tiposPermitidos() {
            if (!this.estagio) {
                return [];
            }
            if (this.estagio.status === 'ATIVO') {
                return ['TROCA_MODALIDADE', 'TRANCAMENTO'];
            }
            const hoje = new Date();
            const hojeTexto = new Date(hoje.getTime() - hoje.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
            if (this.estagio.status === 'SUSPENSO' && this.prazoReabertura && hojeTexto <= this.prazoReabertura) {
                return ['REABERTURA'];
            }
            return [];
        },

        temEmAnalise() {
            return this.solicitacoes.some(s => s.tipo !== 'EXCLUSAO_ANEXO'
                && (s.status === 'PENDENTE' || s.status === 'EM_ANALISE_COORDENACAO'));
        },

        validar() {
            this.erros = {};
            if (!this.form.tipo) {
                this.erros.tipo = 'Escolha o tipo da solicitação';
            }
            if (this.form.tipo === 'TROCA_MODALIDADE' && !this.form.novoTipoEstagioId) {
                this.erros.novoTipoEstagioId = 'Escolha o novo tipo de estágio';
            }
            if (!this.form.motivo.trim()) {
                this.erros.motivo = 'Informe o motivo da solicitação';
            }
            return Object.keys(this.erros).length === 0;
        },

        async enviar() {
            this.erro = '';
            this.sucesso = '';
            if (!this.validar()) {
                return;
            }
            this.salvando = true;
            const corpo = {
                tipo: this.form.tipo,
                novoTipoEstagioId: this.form.tipo === 'TROCA_MODALIDADE' ? Number(this.form.novoTipoEstagioId) : null,
                motivo: this.form.motivo.trim()
            };
            const resposta = await chamarApiSolicitacoes('/api/v1/aluno/solicitacoes', 'POST', corpo);
            this.salvando = false;
            if (resposta.ok) {
                this.form = { tipo: '', novoTipoEstagioId: '', motivo: '' };
                this.sucesso = 'Solicitação enviada.';
                await this.carregar();
            } else {
                this.erros = resposta.dados.campos || {};
                this.erro = resposta.dados.mensagem || 'Não foi possível enviar a solicitação.';
            }
        },

        async cancelar(solicitacao) {
            this.erro = '';
            this.sucesso = '';
            this.salvando = true;
            const resposta = await chamarApiSolicitacoes(`/api/v1/aluno/solicitacoes/${solicitacao.id}/cancelar`, 'POST');
            this.salvando = false;
            this.cancelandoId = null;
            if (resposta.ok) {
                this.sucesso = 'Solicitação cancelada.';
                await this.carregar();
            } else {
                this.erro = resposta.dados.mensagem || 'Não foi possível cancelar a solicitação.';
            }
        }
    };
}
