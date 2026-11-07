// Script da página orientador/historico.html: correções feitas pelo orientador com paginação

function historicoCorrecoes() {
    return {
        historico: null,
        carregando: true,
        erro: '',
        pagina: 0,

        nomeStatus: nomeStatusEntrega,
        classeStatus: classeStatusEntrega,
        formatarDataHora: formatarDataHoraEntrega,

        async carregar() {
            this.carregando = true;
            this.erro = '';
            const parametros = new URLSearchParams({ pagina: this.pagina, tamanho: 10 });
            try {
                const resp = await fetch('/api/v1/orientador/entregas/historico?' + parametros, { credentials: 'include' });
                if (resp.ok) {
                    this.historico = await resp.json();
                } else {
                    this.erro = (await mensagemErroApi(resp, 'Não foi possível carregar o histórico.')).mensagem;
                }
            } catch (e) {
                this.erro = 'Não foi possível conectar ao servidor.';
            } finally {
                this.carregando = false;
            }
        },

        irPara(pagina) {
            this.pagina = pagina;
            this.carregar();
        }
    };
}
