// Script da página orientador/correcoes.html: painel de correção com filtros combináveis
function painelCorrecao() {
    return {
        painel: null,
        carregando: true,
        erro: '',
        filtros: { alunoId: '', tarefaId: '', status: '', de: '', ate: '' },
        pagina: 0,

        nomeStatus: nomeStatusEntrega,
        classeStatus: classeStatusEntrega,
        formatarDataHora: formatarDataHoraEntrega,

        aguardando(status) {
            return status === 'ENVIADA' || status === 'EM_REVISAO';
        },

        async carregar() {
            this.carregando = true;
            this.erro = '';
            const parametros = new URLSearchParams({ pagina: this.pagina, tamanho: 10 });
            for (const [chave, valor] of Object.entries(this.filtros)) {
                if (valor) {
                    parametros.append(chave, valor);
                }
            }
            try {
                const resp = await fetch('/api/v1/orientador/entregas?' + parametros, { credentials: 'include' });
                if (resp.ok) {
                    this.painel = await resp.json();
                } else {
                    this.erro = (await mensagemErroApi(resp, 'Não foi possível carregar o painel.')).mensagem;
                }
            } catch (e) {
                this.erro = 'Não foi possível conectar ao servidor.';
            } finally {
                this.carregando = false;
            }
        },

        aplicarFiltros() {
            if (this.filtros.de && this.filtros.ate && this.filtros.de > this.filtros.ate) {
                this.erro = 'A data inicial do período deve ser anterior à final.';
                return;
            }
            this.pagina = 0;
            this.carregar();
        },

        limparFiltros() {
            this.filtros = { alunoId: '', tarefaId: '', status: '', de: '', ate: '' };
            this.pagina = 0;
            this.carregar();
        },

        irPara(pagina) {
            this.pagina = pagina;
            this.carregar();
        }
    };
}
