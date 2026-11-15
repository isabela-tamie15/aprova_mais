/* =============================================
   Aprova+ - Funções das telas de cadastro da coordenação
   ============================================= */

/*
   Faz a chamada à API e devolve sempre o mesmo formato: { ok, status, dados }.
   Assim cada tela só olha o ok e mostra dados.mensagem (ou dados.campos, no
   erro de validação), sem repetir o tratamento de rede e de erro 500.
   O corpo é opcional; quando vem, é enviado como JSON
*/
async function requisicaoJson(url, metodo = 'GET', corpo = null) {
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

    // 204 não tem corpo, e alguns erros (como o 403) também podem vir vazios
    let dados = null;
    if (resposta.status !== 204) {
        // Lemos o corpo como JSON. Se vier vazio ou não for JSON, ficamos com um objeto vazio
        try {
            dados = await resposta.json();
        } catch (erroDeLeitura) {
            dados = {};
        }
    }

    if (resposta.status >= 500) {
        return { ok: false, status: resposta.status, dados: { mensagem: 'Erro interno no servidor. Tente novamente.' } };
    }
    if (resposta.status === 403) {
        return { ok: false, status: 403, dados: { mensagem: 'Você não tem permissão para esta ação.' } };
    }
    return { ok: resposta.ok, status: resposta.status, dados: dados };
}

// Rola a página até o formulário, usado quando o coordenador clica em Editar numa linha da tabela
function rolarParaFormulario(elemento) {
    if (elemento) {
        elemento.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
}

// Nomes dos enums para mostrar na tela, nos selects e nas tabelas
const NOMES_PERIODO = { MATUTINO: 'Matutino', NOTURNO: 'Noturno', NAO_APLICAVEL: 'Não se aplica' };
const NOMES_MODALIDADE = { PRESENCIAL: 'Presencial', EAD: 'EAD', HIBRIDO: 'Híbrido' };

/*
   Carrega os cursos do coordenador logado, usado no select de curso das telas
   de turmas, tipos de estágio e trilhas. Devolve a lista, ou lista vazia se der erro
*/
async function carregarCursosDoCoordenador() {
    const resposta = await requisicaoJson('/api/v1/coordenador/cursos');
    return resposta.ok ? resposta.dados : [];
}
