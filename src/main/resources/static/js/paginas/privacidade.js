// Página privacidade.html

function direitosDoTitular() {
    return {
        // Direito escolhido no select, que pode ser acesso, correcao ou exclusao
        direito: '',

        // A senha é sempre pedida, e o código para quem usa a verificação em duas etapas
        senha: '',
        codigo: '',
        erro: '',

        // Acesso e correção registram o pedido na auditoria e abrem o e-mail para a secretaria
        async enviarSolicitacao() {
            let endereco = '/api/v1/lgpd/solicitar-correcao';
            let assuntoDoEmail = 'Aprova%2B%20-%20Solicitação%20de%20Correção%20de%20Dados';
            if (this.direito === 'acesso') {
                endereco = '/api/v1/lgpd/solicitar-acesso';
                assuntoDoEmail = 'Aprova%2B%20-%20Solicitação%20de%20Acesso%20aos%20Dados';
            }

            // Mesmo se o registro falhar, abrimos o e-mail, para o pedido chegar à secretaria
            try {
                await fetch(endereco, { method: 'POST', credentials: 'include' });
            } catch (erro) {
                console.error('Não foi possível registrar a solicitação', erro);
            }
            window.open('mailto:secretaria@umc.br?subject=' + assuntoDoEmail);
        },

        // Exclusão anonimiza os dados e encerra a sessão
        async anonimizarMeusDados() {
            const confirmou = confirm('Confirma a anonimização dos seus dados pessoais? Esta ação não pode ser desfeita.');
            if (!confirmou) {
                return;
            }

            this.erro = '';
            try {
                const resposta = await fetch('/api/v1/lgpd/meus-dados', {
                    method: 'DELETE',
                    credentials: 'include',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ senha: this.senha, codigo: this.codigo })
                });
                if (!resposta.ok) {
                    // Senha ou código errado, conta bloqueada e casos não permitidos trazem a mensagem do servidor
                    let dados = {};
                    try {
                        dados = await resposta.json();
                    } catch (erroDeLeitura) {
                        dados = {};
                    }
                    this.codigo = '';
                    this.erro = dados.mensagem || 'Erro ao processar. Tente novamente.';
                    return;
                }
            } catch (erro) {
                this.erro = 'Não foi possível conectar ao servidor.';
                return;
            }

            // A conta foi anonimizada, então fazemos o logout e voltamos para o login
            try {
                await fetch('/api/v1/auth/logout', { method: 'POST', credentials: 'include' });
            } catch (erro) {
                console.error('Não foi possível fazer o logout', erro);
            }
            alert('Seus dados foram anonimizados com sucesso.');
            window.location.href = '/login';
        }
    };
}
