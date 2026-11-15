// Funções das telas de solicitações do aluno, da secretaria e da coordenação

const NOMES_TIPO_SOLICITACAO = {
    TROCA_MODALIDADE: 'Troca de modalidade',
    TRANCAMENTO: 'Trancamento',
    REABERTURA: 'Reabertura'
};

const CLASSES_STATUS_SOLICITACAO = {
    PENDENTE: 'bg-warning text-dark',
    EM_ANALISE_COORDENACAO: 'bg-info text-dark',
    APROVADA: 'bg-success',
    REPROVADA: 'bg-danger',
    CANCELADA: 'bg-secondary'
};

// O texto do status depende do tipo, porque o trancamento passa pela secretaria antes da coordenação
function nomeStatusSolicitacao(solicitacao) {
    switch (solicitacao.status) {
        case 'PENDENTE':
            return 'Aguardando a secretaria';
        case 'EM_ANALISE_COORDENACAO':
            return solicitacao.tipo === 'TRANCAMENTO'
                ? 'Validado pela secretaria, aguardando a coordenação'
                : 'Aguardando a coordenação';
        case 'APROVADA':
            return 'Aprovada';
        case 'REPROVADA':
            return 'Reprovada';
        case 'CANCELADA':
            return 'Cancelada';
        default:
            return solicitacao.status;
    }
}

function classeStatusSolicitacao(status) {
    return CLASSES_STATUS_SOLICITACAO[status] || 'bg-secondary';
}

// A API devolve 2026-11-22, e a tela mostra 22/11/2026
function formatarDataSolicitacao(data) {
    return data ? data.split('-').reverse().join('/') : '';
}

function formatarDataHoraSolicitacao(valor) {
    return valor ? new Date(valor).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) : '';
}

// Chama a API e devolve { ok, status, dados }, com a mensagem de erro já pronta para a tela
async function chamarApiSolicitacoes(url, metodo = 'GET', corpo = null) {
    const opcoes = { method: metodo, credentials: 'include', headers: {} };
    if (corpo !== null) {
        opcoes.headers['Content-Type'] = 'application/json';
        opcoes.body = JSON.stringify(corpo);
    }
    let resposta;
    try {
        resposta = await fetch(url, opcoes);
    } catch (erro) {
        return { ok: false, status: 0, dados: { mensagem: 'Não foi possível conectar ao servidor.' } };
    }
    if (resposta.status === 204) {
        return { ok: true, status: 204, dados: null };
    }
    let dados = {};
    try {
        dados = await resposta.json();
    } catch (erroDeLeitura) {
        dados = {};
    }
    if (resposta.status >= 500) {
        dados = { mensagem: 'Erro interno no servidor. Tente novamente.' };
    }
    return { ok: resposta.ok, status: resposta.status, dados: dados };
}

/*
   Lista de pedidos com a decisão, usada pela secretaria e pela coordenação. A coordenação escolhe
   o curso, e a secretaria vê todos os pedidos de trancamento e de reabertura
*/
function painelSolicitacoes(urlBase, statusDaVez, usaCurso) {
    return {
        cursos: [],
        cursoId: '',
        solicitacoes: [],
        filtro: 'aguardando',
        carregando: true,
        salvando: false,
        erro: '',
        sucesso: '',
        reprovandoId: null,
        justificativa: '',
        nomeTipo: NOMES_TIPO_SOLICITACAO,
        nomeStatus: nomeStatusSolicitacao,
        classeStatus: classeStatusSolicitacao,
        formatarData: formatarDataSolicitacao,
        formatarDataHora: formatarDataHoraSolicitacao,

        async init() {
            if (usaCurso) {
                const resposta = await chamarApiSolicitacoes('/api/v1/coordenador/cursos');
                this.cursos = resposta.ok ? resposta.dados : [];
                if (this.cursos.length > 0) {
                    this.cursoId = String(this.cursos[0].id);
                }
            }
            await this.carregar();
            this.carregando = false;
        },

        async carregar() {
            this.reprovandoId = null;
            if (usaCurso && !this.cursoId) {
                this.solicitacoes = [];
                return;
            }
            const url = usaCurso ? urlBase + '?cursoId=' + this.cursoId : urlBase;
            const resposta = await chamarApiSolicitacoes(url);
            if (resposta.ok) {
                this.solicitacoes = resposta.dados;
            } else {
                this.erro = resposta.dados.mensagem || 'Erro ao carregar as solicitações.';
            }
        },

        visiveis() {
            if (this.filtro === 'todas') {
                return this.solicitacoes;
            }
            return this.solicitacoes.filter(solicitacao => solicitacao.status === statusDaVez);
        },

        aguardando() {
            return this.solicitacoes.filter(solicitacao => solicitacao.status === statusDaVez).length;
        },

        podeDecidir(solicitacao) {
            return solicitacao.status === statusDaVez;
        },

        async aprovar(solicitacao) {
            await this.decidir(solicitacao, 'aprovar', null, 'Solicitação aprovada.');
        },

        abrirReprovacao(solicitacao) {
            this.reprovandoId = solicitacao.id;
            this.justificativa = '';
            this.erro = '';
        },

        async reprovar(solicitacao) {
            if (!this.justificativa.trim()) {
                this.erro = 'Informe a justificativa da reprovação.';
                return;
            }
            await this.decidir(solicitacao, 'reprovar', { justificativa: this.justificativa.trim() }, 'Solicitação reprovada.');
        },

        async decidir(solicitacao, acao, corpo, mensagemSucesso) {
            this.erro = '';
            this.sucesso = '';
            this.salvando = true;
            const resposta = await chamarApiSolicitacoes(`${urlBase}/${solicitacao.id}/${acao}`, 'POST', corpo);
            this.salvando = false;
            if (resposta.ok) {
                this.sucesso = mensagemSucesso;
                await this.carregar();
            } else {
                this.erro = resposta.dados.mensagem || 'Não foi possível registrar a decisão.';
            }
        }
    };
}
