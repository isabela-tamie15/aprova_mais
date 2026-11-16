// Funções usadas nas telas de entregas do aluno e do orientador

const NOMES_STATUS_ENTREGA = {
    PENDENTE: 'Não enviada',
    ENVIADA: 'Enviada',
    EM_REVISAO: 'Em revisão',
    APROVADA: 'Aprovada',
    DEVOLVIDA: 'Devolvida'
};

const CLASSES_STATUS_ENTREGA = {
    PENDENTE: 'bg-secondary',
    ENVIADA: 'bg-primary',
    EM_REVISAO: 'bg-info text-dark',
    APROVADA: 'bg-success',
    DEVOLVIDA: 'bg-warning text-dark'
};

// Resultado de cada critério do checklist, marcado pelo orientador na correção
const NOMES_RESULTADO_CRITERIO = {
    SUFICIENTE: 'Suficiente',
    INSUFICIENTE: 'Insuficiente'
};

const CLASSES_RESULTADO_CRITERIO = {
    SUFICIENTE: 'bg-success',
    INSUFICIENTE: 'bg-danger'
};

const FORMATOS_ENTREGA = ['pdf', 'docx', 'xlsx', 'pptx', 'jpg', 'jpeg', 'png'];
const TAMANHO_MAXIMO_ENTREGA = 10 * 1024 * 1024;

function nomeStatusEntrega(status) {
    return NOMES_STATUS_ENTREGA[status] || status;
}

function classeStatusEntrega(status) {
    return CLASSES_STATUS_ENTREGA[status] || 'bg-secondary';
}

function nomeResultadoCriterio(resultado) {
    return NOMES_RESULTADO_CRITERIO[resultado] || resultado;
}

function classeResultadoCriterio(resultado) {
    return CLASSES_RESULTADO_CRITERIO[resultado] || 'bg-secondary';
}

// A API devolve a data com fuso, como 2026-11-02T19:30:00-03:00, e a tela mostra 02/11/2026 19:30
function formatarDataHoraEntrega(valor) {
    if (!valor) {
        return '';
    }
    return new Date(valor).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
}

function formatarTamanhoArquivo(bytes) {
    if (bytes < 1024) {
        return bytes + ' B';
    }
    if (bytes < 1024 * 1024) {
        return (bytes / 1024).toFixed(0) + ' KB';
    }
    return (bytes / (1024 * 1024)).toFixed(1).replace('.', ',') + ' MB';
}

// Lê a mensagem de erro da API. O erro 500 vira uma mensagem genérica
async function mensagemErroApi(resposta, padrao) {
    if (resposta.status >= 500) {
        return { mensagem: 'Erro interno no servidor. Tente novamente.', campos: {} };
    }
    // Lemos o corpo da resposta como JSON. Se vier vazio ou não for JSON, ficamos com um objeto vazio
    let dados = {};
    try {
        dados = await resposta.json();
    } catch (erroDeLeitura) {
        dados = {};
    }
    return { mensagem: dados.mensagem || padrao, campos: dados.campos || {} };
}
