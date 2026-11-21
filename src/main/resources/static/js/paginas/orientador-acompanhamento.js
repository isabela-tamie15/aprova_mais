// Página orientador/acompanhamento.html, com a visão consolidada de um aluno

function acompanhamentoAluno(estagioId) {
    return {
        visao: null,
        carregando: true,
        erro: '',
        erroFinalizacao: '',
        observacao: '',
        processando: false,
        confirmandoConclusao: false,
        confirmandoReprovacao: false,

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

        async concluir() {
            await this.finalizar(`/api/v1/orientador/estagios/${estagioId}/concluir`, null, 'Não foi possível concluir o estágio.');
        },

        async reprovar() {
            if (!this.observacao.trim()) {
                this.erroFinalizacao = 'Informe a observação da reprovação.';
                return;
            }
            await this.finalizar(`/api/v1/orientador/estagios/${estagioId}/reprovar`,
                { observacao: this.observacao.trim() }, 'Não foi possível reprovar o estágio.');
        },

        // Envia a finalização e volta para a lista de estágios, porque o estágio deixa de estar ativo
        async finalizar(url, corpo, mensagemPadrao) {
            this.erroFinalizacao = '';
            this.processando = true;
            try {
                const opcoes = { method: 'POST', credentials: 'include', headers: {} };
                if (corpo) {
                    opcoes.headers['Content-Type'] = 'application/json';
                    opcoes.body = JSON.stringify(corpo);
                }
                const resposta = await fetch(url, opcoes);
                if (resposta.ok) {
                    window.location.href = '/orientador/validacoes';
                    return;
                }
                const dados = await mensagemErroApi(resposta, mensagemPadrao);
                this.erroFinalizacao = dados.campos.observacao || dados.mensagem;
            } catch (erro) {
                this.erroFinalizacao = 'Não foi possível conectar ao servidor.';
            } finally {
                this.processando = false;
            }
        },

        // As horas vêm prontas do backend, e aqui só saem as casas decimais
        horas(valor) {
            return valor == null ? '-' : Math.round(Number(valor)) + 'h';
        },

        // A API devolve a data no formato 2026-06-15, e a tela mostra 15/06/2026
        formatarData(data) {
            return data ? data.split('-').reverse().join('/') : '';
        }
    };
}
