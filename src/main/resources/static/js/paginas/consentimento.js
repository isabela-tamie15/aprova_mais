// Página consentimento.html

function consentimentoForm() {
    return {
        aceitou: false,
        carregando: false,
        erro: '',
        async aceitar() {
            if (!this.aceitou) return;
            this.carregando = true;
            this.erro = '';
            try {
                const resposta = await fetch('/api/v1/consentimento/aceitar', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' }
                });
                if (resposta.ok) {
                    window.location.href = '/inicio';
                } else {
                    this.erro = 'Erro ao registrar o aceite. Tente novamente.';
                }
            } catch (erro) {
                this.erro = 'Erro de conexão. Tente novamente.';
            } finally {
                this.carregando = false;
            }
        }
    }
}
