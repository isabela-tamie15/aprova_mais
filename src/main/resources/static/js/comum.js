/* =============================================
   Aprova+ - Funções usadas em várias páginas
   ============================================= */

// Faz logout do usuário, invalida o cookie JWT no backend e volta para a tela de login
async function logout() {
    await fetch('/api/v1/auth/logout', {
        method: 'POST',
        credentials: 'include'
    });
    window.location.href = '/login';
}

/*
   Quando a versão dos termos muda, a API responde 403 com consentimentoPendente até o novo aceite.
   Qualquer chamada da página que receber essa resposta leva o usuário à tela de consentimento
*/
const fetchOriginal = window.fetch.bind(window);
window.fetch = async function (...argumentos) {
    const resposta = await fetchOriginal(...argumentos);
    if (resposta.status === 403) {
        try {
            const dados = await resposta.clone().json();
            if (dados.consentimentoPendente) {
                window.location.href = '/consentimento';
            }
        } catch (erroDeLeitura) {
            // Resposta sem JSON segue para a página tratar
        }
    }
    return resposta;
};

// Liga os botões Sair e Voltar das barras de navegação. O clique é tratado aqui porque a política de conteúdo não aceita onclick nem links javascript: no HTML
document.addEventListener('click', (evento) => {
    if (evento.target.closest('[data-acao="sair"]')) {
        logout();
    } else if (evento.target.closest('[data-acao="voltar"]')) {
        evento.preventDefault();
        voltarParaPaginaAnterior();
    }
});

function voltarParaPaginaAnterior() {
    if (history.length > 1) {
        history.back();
        return;
    }
    window.close();
    setTimeout(() => { window.location.href = '/inicio'; }, 200);
}

/*
   Janela de confirmação no lugar do confirm() do navegador, com os estilos do Bootstrap. Devolve uma
   Promise que resolve em true quando a pessoa confirma e em false quando cancela, fecha ou aperta Esc
*/
function confirmarAcao(mensagem, textoConfirmar = 'Confirmar', perigosa = false) {
    return abrirJanela(mensagem, textoConfirmar, perigosa ? 'btn-danger' : 'btn-primary', true);
}

// Aviso com um botão só, no lugar do alert() do navegador. A Promise resolve quando a pessoa fecha a janela
function mostrarAviso(mensagem) {
    return abrirJanela(mensagem, 'Entendi', 'btn-primary', false);
}

/*
   Monta a janela no fim da página e a remove ao fechar. A mensagem entra como texto, nunca como HTML,
   para um nome vindo da API não virar código na página
*/
function abrirJanela(mensagem, textoConfirmar, classeBotao, comCancelar) {
    return new Promise((resolver) => {
        const anterior = document.activeElement;
        const fundo = document.createElement('div');
        fundo.className = 'modal-backdrop fade show';

        const janela = document.createElement('div');
        janela.className = 'modal fade show d-block';
        janela.tabIndex = -1;
        janela.setAttribute('role', 'dialog');
        janela.setAttribute('aria-modal', 'true');
        janela.innerHTML =
            '<div class="modal-dialog modal-dialog-centered">' +
            '  <div class="modal-content border-0 shadow">' +
            '    <div class="modal-body p-4"><p class="mb-0 janela-mensagem"></p></div>' +
            '    <div class="modal-footer border-0 pt-0">' +
            '      <button type="button" class="btn btn-secondary janela-cancelar">Cancelar</button>' +
            '      <button type="button" class="btn janela-confirmar"></button>' +
            '    </div>' +
            '  </div>' +
            '</div>';
        janela.querySelector('.janela-mensagem').textContent = mensagem;
        const botaoConfirmar = janela.querySelector('.janela-confirmar');
        botaoConfirmar.textContent = textoConfirmar;
        botaoConfirmar.classList.add(classeBotao);
        const botaoCancelar = janela.querySelector('.janela-cancelar');
        if (!comCancelar) {
            botaoCancelar.remove();
        }

        function fechar(resultado) {
            document.removeEventListener('keydown', teclado);
            janela.remove();
            fundo.remove();
            document.body.classList.remove('modal-open');
            if (anterior && anterior.focus) {
                anterior.focus();
            }
            resolver(resultado);
        }

        function teclado(evento) {
            if (evento.key === 'Escape') {
                fechar(false);
            }
        }

        botaoConfirmar.addEventListener('click', () => fechar(true));
        botaoCancelar.addEventListener('click', () => fechar(false));
        // Clicar fora da caixa equivale a cancelar
        janela.addEventListener('click', (evento) => {
            if (evento.target === janela) {
                fechar(false);
            }
        });
        document.addEventListener('keydown', teclado);

        document.body.appendChild(fundo);
        document.body.appendChild(janela);
        document.body.classList.add('modal-open');
        botaoConfirmar.focus();
    });
}
