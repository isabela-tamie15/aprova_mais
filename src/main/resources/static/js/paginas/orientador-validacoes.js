// Página orientador/validacoes.html

function validacao(id) {
    return {
        justificativa: '',
        mensagem: '',

        // Mesmo limite do backend para a data de início confirmada
        dataMaxima() {
            const dataLimite = new Date();
            dataLimite.setFullYear(dataLimite.getFullYear() + 1);
            // ajusta o fuso para pegar a data local, e não a UTC
            return new Date(dataLimite.getTime() - dataLimite.getTimezoneOffset() * 60000)
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
                const resposta = await fetch(`/api/v1/orientador/validacoes/${id}/aprovar`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    credentials: 'include',
                    body: JSON.stringify({ dataInicio: this.$refs.dataInicio.value || null })
                });
                if (resposta.ok) {
                    window.location.reload();
                } else {
                    const dados = await resposta.json();
                    this.mensagem = dados.mensagem || 'Erro ao aprovar.';
                }
            } catch (erro) {
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
                const resposta = await fetch(`/api/v1/orientador/validacoes/${id}/rejeitar`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    credentials: 'include',
                    body: JSON.stringify({ justificativa: this.justificativa })
                });
                if (resposta.ok) {
                    window.location.reload();
                } else {
                    const dados = await resposta.json();
                    this.mensagem = dados.mensagem || 'Erro ao rejeitar.';
                }
            } catch (erro) {
                this.mensagem = 'Não foi possível conectar ao servidor.';
            } finally {
                this.processando = false;
            }
        }
    }
}
