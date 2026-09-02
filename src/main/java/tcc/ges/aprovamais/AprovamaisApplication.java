package tcc.ges.aprovamais;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;


@EnableJpaAuditing
@SpringBootApplication
public class AprovamaisApplication {

	public static void main(String[] args) {
		SpringApplication.run(AprovamaisApplication.class, args);
	}

}
