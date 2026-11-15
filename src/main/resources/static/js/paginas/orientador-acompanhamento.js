// Script da página orientador/acompanhamento.html: visão consolidada de um aluno

function acompanhamentoAluno(estagioId) {
    return {
        visao: null,
        carregando: true,
        erro: '',

        nomeStatus: nomeStatusEntrega,
        classeStatus: classeStatusEntrega,
        formatarDataHora: formatarDataHoraEntrega,

        async carregar() {
            try {
                const resposta = await fetch(`/api/v1/orientador/estagios/${estagioId}/visao`, { credentials: 'include' });
                if (resposta.ok) {
                    this.visao = await resposta.json();
                } else {
                    this.erro = (await mensagemErroApi(resposta, 'Não foi possível carregar o acompanhamento.')).mensagem;
                }
            } catch (erro) {
                this.erro = 'Não foi possível conectar ao servidor.';
            } finally {
                this.carregando = false;
            }
        },

        // As horas vêm prontas do Java; aqui só tiramos as casas decimais para exibir
        horas(valor) {
            return valor == null ? '-' : Math.round(Number(valor)) + 'h';
        },

        // A API devolve a data no formato 2026-06-15, e a tela mostra 15/06/2026
        formatarData(data) {
            return data ? data.split('-').reverse().join('/') : '';
        }
    };
}
