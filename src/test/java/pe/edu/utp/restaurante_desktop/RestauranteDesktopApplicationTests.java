package pe.edu.utp.restaurante_desktop;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = pe.edu.utp.restaurante.RestauranteApplication.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:contexttest;MODE=PostgreSQL",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class RestauranteDesktopApplicationTests {

	@Test
	void contextLoads() {
	}

}
