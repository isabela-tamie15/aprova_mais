// Script da página secretaria/enviar-convite.html, que antes ficava num <script> dentro dela

function conviteForm() {
    return {
        form: { emailDestino: '', perfil: '' },
        enviando: false,
        erro: '',
        erros: {},
        sucesso: false,

        podoEnviar() {
            return this.form.emailDestino && this.form.perfil;
        },

        // Conferência simples do formato, a validação completa é feita no backend
        validar() {
            const erros = {};
            const email = this.form.emailDestino.trim();
            if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
                erros.emailDestino = 'Informe um e-mail válido.';
            } else if (email.length > 254) {
                erros.emailDestino = 'O e-mail deve ter no máximo 254 caracteres.';
            }
            if (!this.form.perfil) {
                erros.perfil = 'Selecione o perfil de acesso.';
            }
            this.erros = erros;
            return Object.keys(erros).length === 0;
        },

        async enviar() {
            this.erro = '';
            this.sucesso = false;
            if (!this.validar()) {
                return;
            }
            this.form.emailDestino = this.form.emailDestino.trim();
            this.enviando = true;

            try {
                const resp = await fetch('/api/v1/convites', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    credentials: 'include',
                    body: JSON.stringify(this.form)
                });

                const dados = await resp.json().catch(() => ({}));

                if (!resp.ok) {
                    if (resp.status >= 500) {
                        this.erro = 'Erro interno no servidor. Tente novamente.';
                    } else {
                        if (dados.campos) {
                            this.erros = dados.campos;
                        }
                        this.erro = dados.mensagem || 'Erro ao enviar convite.';
                    }
                    return;
                }

                this.sucesso = true;
                this.form = { emailDestino: '', perfil: '' };

            } catch (e) {
                this.erro = 'Não foi possível conectar ao servidor.';
            } finally {
                this.enviando = false;
            }
        }
    }
}
