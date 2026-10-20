// Script da página consentimento.html, que antes ficava num <script> dentro dela

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
                const response = await fetch('/api/v1/consentimento/aceitar', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' }
                });
                if (response.ok) {
                    window.location.href = '/inicio';
                } else {
                    this.erro = 'Erro ao registrar o aceite. Tente novamente.';
                }
            } catch (e) {
                this.erro = 'Erro de conexão. Tente novamente.';
            } finally {
                this.carregando = false;
            }
        }
    }
}
