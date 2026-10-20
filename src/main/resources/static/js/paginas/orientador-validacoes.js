// Script da página orientador/validacoes.html, que antes ficava num <script> dentro dela

function validacao(id) {
    return {
        justificativa: '',
        mensagem: '',

        // Mesmo limite do backend para a data de início confirmada
        dataMaxima() {
            const data = new Date();
            data.setFullYear(data.getFullYear() + 1);
            // ajusta o fuso pra pegar a data local, e não a UTC
            return new Date(data.getTime() - data.getTimezoneOffset() * 60000)
                    .toISOString().slice(0, 10);
        },
        processando: false,

        async aprovar() {
            const dataInicio = this.$refs.dataInicio.value;
            if (dataInicio && dataInicio > this.dataMaxima()) {
                this.mensagem = 'A data de início não pode ser mais de 1 ano no futuro.';
                return;
            }
            this.mensagem = '';
            this.processando = true;
            try {
                const resp = await fetch(`/api/v1/orientador/validacoes/${id}/aprovar`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    credentials: 'include',
                    body: JSON.stringify({ dataInicio: this.$refs.dataInicio.value || null })
                });
                if (resp.ok) {
                    window.location.reload();
                } else {
                    const dados = await resp.json();
                    this.mensagem = dados.mensagem || 'Erro ao aprovar.';
                }
            } catch (e) {
                this.mensagem = 'Não foi possível conectar ao servidor.';
            } finally {
                this.processando = false;
            }
        },

        async rejeitar() {
            if (!this.justificativa.trim()) {
                this.mensagem = 'Informe a justificativa antes de rejeitar.';
                return;
            }
            if (this.justificativa.length > 1000) {
                this.mensagem = 'A justificativa deve ter no máximo 1000 caracteres.';
                return;
            }
            this.processando = true;
            try {
                const resp = await fetch(`/api/v1/orientador/validacoes/${id}/rejeitar`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    credentials: 'include',
                    body: JSON.stringify({ justificativa: this.justificativa })
                });
                if (resp.ok) {
                    window.location.reload();
                } else {
                    const dados = await resp.json();
                    this.mensagem = dados.mensagem || 'Erro ao rejeitar.';
                }
            } catch (e) {
                this.mensagem = 'Não foi possível conectar ao servidor.';
            } finally {
                this.processando = false;
            }
        }
    }
}
