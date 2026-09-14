package tacos.web.api.integration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.autoconfigure.data.rest.RepositoryRestMvcAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.http.*;
import tacos.Ingredient;
import tacos.web.api.IngredientController;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(classes=IngredientMongoIT.Application.class,
    webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties={"server.servlet.context-path=/course", "spring.main.web-application-type=servlet", "taco.api.allowed-origin=http://localhost:8080"})
class IngredientMongoIT {
  @Container
  static MongoDBContainer mongo = new MongoDBContainer("mongo:4.4.29");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);
  }

  @Autowired TestRestTemplate http;

  @SpringBootConfiguration
  @EnableAutoConfiguration(exclude={SecurityAutoConfiguration.class,
      UserDetailsServiceAutoConfiguration.class, RepositoryRestMvcAutoConfiguration.class})
  @EnableReactiveMongoRepositories(basePackages="tacos.data")
  @Import(IngredientController.class)
  static class Application { }

  @Test
  void createUpdateReadDeleteAgainstRealMongo() {
    Ingredient ingredient = new Ingredient("integration-A", "Original", Ingredient.Type.WRAP);
    ResponseEntity<Ingredient> created = http.postForEntity("/api/ingredients", ingredient, Ingredient.class);
    assertEquals(HttpStatus.CREATED, created.getStatusCode());
    assertNotNull(created.getHeaders().getLocation());
    assertTrue(created.getHeaders().getLocation().toString().contains("/course/api/ingredients/integration-A"));
    assertEquals(HttpStatus.OK, http.getForEntity(created.getHeaders().getLocation(), Ingredient.class).getStatusCode());
    ingredient.setName("Updated");
    assertEquals(HttpStatus.OK, http.exchange("/api/ingredients/integration-A", HttpMethod.PUT,
        new HttpEntity<>(ingredient), Ingredient.class).getStatusCode());
    assertEquals("Updated", http.getForObject("/api/ingredients/integration-A", Ingredient.class).getName());
    assertEquals(HttpStatus.NO_CONTENT, http.exchange("/api/ingredients/integration-A", HttpMethod.DELETE,
        HttpEntity.EMPTY, String.class).getStatusCode());
    assertEquals(HttpStatus.NOT_FOUND, http.getForEntity("/api/ingredients/integration-A", String.class).getStatusCode());
    assertEquals(HttpStatus.NOT_FOUND, http.exchange("/api/ingredients/integration-A", HttpMethod.DELETE,
        HttpEntity.EMPTY, String.class).getStatusCode());
    ingredient.setId("missing");
    assertEquals(HttpStatus.NOT_FOUND, http.exchange("/api/ingredients/missing", HttpMethod.PUT,
        new HttpEntity<>(ingredient), String.class).getStatusCode());
    assertEquals(HttpStatus.NOT_FOUND, http.getForEntity("/api/ingredients/missing", String.class).getStatusCode());
  }
}
