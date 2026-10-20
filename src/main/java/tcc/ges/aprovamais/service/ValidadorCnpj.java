package tcc.ges.aprovamais.service;

import java.util.Locale;

/*
   Confere os dígitos verificadores do CNPJ, sem consultar nada fora
   Já aceita o CNPJ alfanumérico da Receita. No cálculo, cada caractere vale o código ASCII menos 48: os números continuam 0 a 9 e as
   letras vão de A = 17 até Z = 42
   Para o CNPJ só com números, a conta é a mesma de sempre
*/
public final class ValidadorCnpj {

    private static final int[] PESOS_PRIMEIRO = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] PESOS_SEGUNDO = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private ValidadorCnpj() {
    }

    // Tira pontos, barra, traço e espaços e deixa as letras maiúsculas
    public static String normalizar(String cnpj) {
        if (cnpj == null) {
            return null;
        }
        return cnpj.replaceAll("[.\\-/\\s]", "").toUpperCase(Locale.ROOT);
    }

    public static boolean valido(String cnpj) {
        String valor = normalizar(cnpj);
        if (valor == null || !valor.matches("[0-9A-Z]{12}[0-9]{2}")) {
            return false;
        }

        if (valor.chars().distinct().count() == 1) {
            return false;
        }

        int primeiro = calcularDigito(valor, PESOS_PRIMEIRO);
        int segundo = calcularDigito(valor, PESOS_SEGUNDO);
        return valor.charAt(12) - '0' == primeiro && valor.charAt(13) - '0' == segundo;
    }

    // Formata pra exibir no formato 11.222.333/0001-81
    public static String formatar(String cnpj) {
        String valor = normalizar(cnpj);
        if (valor == null || valor.length() != 14) {
            return cnpj;
        }
        return valor.substring(0, 2) + "." + valor.substring(2, 5) + "." + valor.substring(5, 8)
                + "/" + valor.substring(8, 12) + "-" + valor.substring(12);
    }

    private static int calcularDigito(String valor, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) {
            soma += (valor.charAt(i) - '0') * pesos[i];
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
