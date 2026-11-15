// Página orientador/historico.html, com as correções feitas pelo orientador e paginação

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
                const resposta = await fetch('/api/v1/orientador/entregas/historico?' + parametros, { credentials: 'include' });
                if (resposta.ok) {
                    this.historico = await resposta.json();
                } else {
                    this.erro = (await mensagemErroApi(resposta, 'Não foi possível carregar o histórico.')).mensagem;
                }
            } catch (erro) {
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
