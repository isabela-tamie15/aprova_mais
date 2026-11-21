function pedidosLiberacao() {
    return {
        endereco: '',
        ocultarVazio: false,
        pedidos: [],
        carregando: true,
        salvando: false,
        recusando: null,
        justificativa: '',
        erro: '',
        sucesso: '',

        async init() {
            this.endereco = this.$el.dataset.endereco;
            this.ocultarVazio = this.$el.classList.contains('ocultar-vazio');
            await this.carregar();
        },

        async carregar() {
            this.carregando = true;
            try {
                const resposta = await fetch(this.endereco, { credentials: 'include' });
                this.pedidos = resposta.ok ? await resposta.json() : [];
                if (!resposta.ok) {
                    this.erro = 'Não foi possível carregar os pedidos.';
                }
            } catch (erro) {
                this.erro = 'Não foi possível conectar ao servidor.';
            } finally {
                this.carregando = false;
            }
        },

        nomeDoPerfil(perfil) {
            const nomes = { ADMINISTRADOR: 'Administrador', COORDENADOR: 'Coordenador', SECRETARIA: 'Secretaria',
                ORIENTADOR: 'Orientador', ALUNO: 'Aluno' };
            return nomes[perfil] || perfil;
        },

        formatarData(valor) {
            return new Date(valor).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
        },

        async aprovar(pedido) {
            const confirmou = await confirmarAcao('Aprovar o pedido de ' + pedido.nome + '? No próximo acesso, a pessoa vai configurar a verificação em duas etapas em um novo celular.', 'Aprovar');
            if (confirmou) {
                await this.enviar(pedido, 'aprovar', null, 'Pedido de ' + pedido.nome + ' aprovado.');
            }
        },

        async recusar(pedido) {
            await this.enviar(pedido, 'recusar', { justificativa: this.justificativa.trim() }, 'Pedido de ' + pedido.nome + ' recusado.');
        },

        async enviar(pedido, acao, corpo, mensagem) {
            this.erro = '';
            this.sucesso = '';
            this.salvando = true;
            try {
                const resposta = await fetch(this.endereco + '/' + pedido.id + '/' + acao, {
                    method: 'POST',
                    credentials: 'include',
                    headers: corpo ? { 'Content-Type': 'application/json' } : {},
                    body: corpo ? JSON.stringify(corpo) : null
                });
                if (resposta.ok) {
                    this.sucesso = mensagem;
                    this.recusando = null;
                    this.justificativa = '';
                    await this.carregar();
                    return;
                }
                let dados = {};
                try {
                    dados = await resposta.json();
                } catch (erroDeLeitura) {
                    dados = {};
                }
                this.erro = dados.mensagem || 'Não foi possível concluir a ação.';
            } catch (erro) {
                this.erro = 'Não foi possível conectar ao servidor.';
            } finally {
                this.salvando = false;
            }
        }
    };
}
