package tcc.ges.aprovamais.config;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.regex.Pattern;

// Mascara os e-mails nas mensagens de log (m***@umc.br). Usada no logback-spring.xml no lugar do %m
public class MascaraEmailConverter extends MessageConverter {

    private static final Pattern EMAIL = Pattern.compile("([A-Za-z0-9])[A-Za-z0-9._%+-]*@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})");

    @Override
    public String convert(ILoggingEvent evento) {
        return mascarar(super.convert(evento));
    }

    static String mascarar(String mensagem) {
        if (mensagem == null || mensagem.indexOf('@') < 0) {
            return mensagem;
        }
        return EMAIL.matcher(mensagem).replaceAll("$1***@$2");
    }
}
