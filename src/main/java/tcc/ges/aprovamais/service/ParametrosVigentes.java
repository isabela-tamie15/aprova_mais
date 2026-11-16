package tcc.ges.aprovamais.service;

import tcc.ges.aprovamais.entity.enums.ParametroSistema;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/*
   Valores em uso dos parâmetros do sistema. O ConfiguracaoService carrega os valores do banco quando a
   aplicação sobe e atualiza aqui a cada alteração. Sem valor carregado, vale o padrão do parâmetro
*/
public final class ParametrosVigentes {

    private static final Map<ParametroSistema, Integer> VALORES = new ConcurrentHashMap<>();

    private ParametrosVigentes() {
    }

    public static int valor(ParametroSistema parametro) {
        return VALORES.getOrDefault(parametro, parametro.getPadrao());
    }

    static void definir(ParametroSistema parametro, int valor) {
        VALORES.put(parametro, valor);
    }

    // Volta todos os parâmetros ao padrão, usado nos testes
    static void restaurarPadroes() {
        VALORES.clear();
    }
}
