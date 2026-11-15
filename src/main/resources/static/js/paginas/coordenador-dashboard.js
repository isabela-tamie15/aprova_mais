// Página coordenador/dashboard.html, que carrega os números do dashboard do coordenador

// Painel vazio, usado antes da primeira resposta da API e quando dá erro, para a tela nunca ler um número que não chegou
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

// A tela não faz contas. Os números, a largura das barras e o texto do prazo vêm prontos do PainelCoordenadorService
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
            const resposta = await requisicaoJson(url);
            this.carregando = false;
            if (resposta.ok) {
                this.painel = resposta.dados;
            } else {
                this.painel = painelVazio();
                this.erro = (resposta.dados && resposta.dados.mensagem) || 'Erro ao carregar o painel.';
            }
        }
    };
}
