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

    let resp;
    try {
        resp = await fetch(url, opcoes);
    } catch (e) {
        return { ok: false, status: 0, dados: { mensagem: 'Não foi possível conectar ao servidor.' } };
    }

    // 204 não tem corpo, e alguns erros (como o 403) também podem vir vazios
    const dados = resp.status === 204 ? null : await resp.json().catch(() => ({}));

    if (resp.status >= 500) {
        return { ok: false, status: resp.status, dados: { mensagem: 'Erro interno no servidor. Tente novamente.' } };
    }
    if (resp.status === 403) {
        return { ok: false, status: 403, dados: { mensagem: 'Você não tem permissão para esta ação.' } };
    }
    return { ok: resp.ok, status: resp.status, dados: dados };
}

// Rola a página até o formulário, usado quando o coordenador clica em Editar numa linha da tabela
function rolarParaFormulario(elemento) {
    if (elemento) {
        elemento.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
}
