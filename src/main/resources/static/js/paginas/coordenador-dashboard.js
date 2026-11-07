// Script da página coordenador/dashboard.html: carrega os números do dashboard do coordenador (RF50)

/*
   Painel vazio, usado antes da primeira resposta da API e quando dá erro.
   Assim a tela nunca tenta ler um número que ainda não chegou
*/
function painelVazio() {
    return {
        estagiosSemOrientador: 0,
        estagiosAguardandoValidacao: 0,
        estagiosEmAndamento: 0,
        turmasSemDataEncerramento: 0,
        tarefasAguardandoCorrecao: 0,
        estagiosRetaFinal: 0,
        retaFinalSoTarefas: 0,
        retaFinalSoHoras: 0,
        retaFinalDuasPendencias: 0,
        situacaoPorTurma: [],
        correcaoPorTurma: [],
        prazoPorTurma: []
    };
}

/*
   A tela não faz nenhuma conta: os números, a largura das barras e o texto do prazo
   já vêm prontos do PainelCoordenadorService. Aqui só buscamos e guardamos a resposta
*/
function painelPagina() {
    return {
        cursos: [],
        cursoId: '',
        painel: painelVazio(),
        carregando: true,
        erro: '',

        async init() {
            this.cursos = await carregarCursosDoCoordenador();
            await this.carregarPainel();
        },

        // Chamado ao abrir a página e cada vez que o coordenador troca o curso no seletor
        async carregarPainel() {
            this.carregando = true;
            this.erro = '';
            const url = this.cursoId
                ? '/api/v1/coordenador/painel?cursoId=' + encodeURIComponent(this.cursoId)
                : '/api/v1/coordenador/painel';
            const r = await requisicaoJson(url);
            this.carregando = false;
            if (r.ok) {
                this.painel = r.dados;
            } else {
                this.painel = painelVazio();
                this.erro = (r.dados && r.dados.mensagem) || 'Erro ao carregar o painel.';
            }
        }
    };
}
