package tcc.ges.aprovamais.dto;

/*
   Resultado da consulta do CNPJ na BrasilAPI.
   ENCONTRADO: a Receita conhece o CNPJ, vem a razão social e a situação cadastral.
   NAO_ENCONTRADO: o CNPJ tem dígitos certos, mas não existe na Receita.
   INDISPONIVEL: a API demorou, caiu ou respondeu algo inesperado. Nesse caso
   o cadastro segue e o orientador confere o CNPJ manualmente no termo de compromisso
*/
public record ConsultaCnpjResponse(Status status, String razaoSocial, String nomeFantasia, String situacao) {

    public enum Status { ENCONTRADO, NAO_ENCONTRADO, INDISPONIVEL }

    public static ConsultaCnpjResponse encontrado(String razaoSocial, String nomeFantasia, String situacao) {
        return new ConsultaCnpjResponse(Status.ENCONTRADO, razaoSocial, nomeFantasia, situacao);
    }

    public static ConsultaCnpjResponse naoEncontrado() {
        return new ConsultaCnpjResponse(Status.NAO_ENCONTRADO, null, null, null);
    }

    public static ConsultaCnpjResponse indisponivel() {
        return new ConsultaCnpjResponse(Status.INDISPONIVEL, null, null, null);
    }
}
