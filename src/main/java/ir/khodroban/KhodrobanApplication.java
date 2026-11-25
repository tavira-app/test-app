package ir.khodroban;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@OpenAPIDefinition(info = @Info(title = "Khodroban API", version = "v1",
        description = "Khodroban backend API"))
public class KhodrobanApplication {
    public static void main(String[] args) {
        SpringApplication.run(KhodrobanApplication.class, args);
    }
}
