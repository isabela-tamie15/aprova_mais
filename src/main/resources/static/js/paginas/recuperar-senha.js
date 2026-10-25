// Script da página recuperar-senha.html: pede o link de recuperação de senha por e-mail

function recuperarSenhaForm() {
    return {
        email: '',
        enviando: false,
        enviado: false,
        mensagem: '',
        erro: '',

        async enviar() {
            this.erro = '';
            const email = this.email.trim();
            if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
                this.erro = 'Informe um e-mail válido.';
                return;
            }

            this.enviando = true;
            try {
                const resp = await fetch('/api/v1/auth/recuperacao/solicitar', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ email: email })
                });
                const dados = await resp.json().catch(() => ({}));

                /*
                   A resposta é a mesma para qualquer e-mail, com ou sem conta, então
                   a tela também mostra sempre a mesma mensagem e esconde o formulário
                */
                if (resp.ok) {
                    this.mensagem = dados.mensagem;
                    this.enviado = true;
                } else if (resp.status >= 500) {
                    this.erro = 'Erro interno no servidor. Tente novamente.';
                } else {
                    this.erro = dados.mensagem || 'Não foi possível enviar o pedido.';
                }
            } catch (e) {
                this.erro = 'Não foi possível conectar ao servidor.';
            } finally {
                this.enviando = false;
            }
        }
    }
}
