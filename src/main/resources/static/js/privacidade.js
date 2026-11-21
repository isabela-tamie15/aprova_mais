// Página privacidade.html

function direitosDoTitular() {
    return {
        // Direito escolhido no select, que pode ser acesso, correcao ou exclusao
        direito: '',

        // A senha é sempre pedida, e o código para quem usa a verificação em duas etapas
        senha: '',
        codigo: '',
        erro: '',
        protocolo: '',
        prazo: '',
        erroSolicitacao: '',

        /*
           Acesso e correção registram o pedido, que recebe um protocolo e o prazo de resposta, e abrem
           o e-mail para o encarregado com o protocolo no assunto
        */
        async enviarSolicitacao() {
            let endereco = '/api/v1/lgpd/solicitar-correcao';
            let assuntoDoEmail = 'Aprova%2B%20-%20Solicitação%20de%20Correção%20de%20Dados';
            if (this.direito === 'acesso') {
                endereco = '/api/v1/lgpd/solicitar-acesso';
                assuntoDoEmail = 'Aprova%2B%20-%20Solicitação%20de%20Acesso%20aos%20Dados';
            }

            this.protocolo = '';
            this.prazo = '';
            this.erroSolicitacao = '';

            // Mesmo se o registro falhar, abrimos o e-mail, para o pedido chegar ao encarregado
            try {
                const resposta = await fetch(endereco, { method: 'POST', credentials: 'include' });
                if (resposta.ok) {
                    const dados = await resposta.json();
                    this.protocolo = dados.protocolo;
                    this.prazo = dados.prazoResposta.split('-').reverse().join('/');
                    assuntoDoEmail += '%20-%20Protocolo%20' + encodeURIComponent(dados.protocolo);
                } else {
                    this.erroSolicitacao = 'Não foi possível registrar a solicitação no sistema. Envie o e-mail assim mesmo.';
                }
            } catch (erro) {
                this.erroSolicitacao = 'Não foi possível conectar ao servidor. Envie o e-mail assim mesmo.';
            }
            window.open('mailto:dpo.aprova_mais@yahoo.com?subject=' + assuntoDoEmail);
        },

        // Exclusão anonimiza os dados e encerra a sessão
        async anonimizarMeusDados() {
            const confirmou = await confirmarAcao('Confirma a anonimização dos seus dados pessoais? Esta ação não pode ser desfeita.',
                'Anonimizar meus dados', true);
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
            await mostrarAviso('Seus dados foram anonimizados com sucesso.');
            window.location.href = '/login';
        }
    };
}
