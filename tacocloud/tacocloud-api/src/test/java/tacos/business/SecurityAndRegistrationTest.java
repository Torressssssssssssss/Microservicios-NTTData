package tacos.business;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.*;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tacos.security.*;
import tacos.data.UserRepository;
import tacos.User;

class SecurityAndRegistrationTest {
  @Configuration @Import({SecurityConfig.class,Probe.class}) static class Config {
    @Bean UserDetailsService users() { return name->org.springframework.security.core.userdetails.User.withUsername(name).password("{noop}test").roles("USER").build(); }
  }
  @RestController static class Probe {
    @RequestMapping({"/api/ingredients","/api/orders","/api/admin/orders","/api/kitchen/queue","/api/users/me/orders","/new-route","/data-api/orders","/actuator/metrics","/actuator/health"})
    public String route() {return "ok";}
  }
  @Test void authorizationMatrixAndDefaultDenial() {
    new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(WebMvcAutoConfiguration.class)).withUserConfiguration(Config.class).run(context->{
      MockMvc mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
      mvc.perform(get("/api/ingredients")).andExpect(status().isOk());
      mvc.perform(post("/api/orders").with(csrf())).andExpect(status().isUnauthorized());
      mvc.perform(post("/api/orders").with(user("u").roles("USER")).with(csrf())).andExpect(status().isOk());
      mvc.perform(post("/api/ingredients").with(user("u").roles("USER")).with(csrf())).andExpect(status().isForbidden());
      mvc.perform(post("/api/ingredients").with(user("a").roles("ADMIN")).with(csrf())).andExpect(status().isOk());
      mvc.perform(get("/api/kitchen/queue").with(user("k").roles("KITCHEN"))).andExpect(status().isOk());
      mvc.perform(get("/api/admin/orders").with(user("k").roles("KITCHEN"))).andExpect(status().isForbidden());
      mvc.perform(get("/new-route").with(user("a").roles("ADMIN"))).andExpect(status().isForbidden());
      mvc.perform(get("/data-api/orders").with(user("a").roles("ADMIN"))).andExpect(status().isForbidden());
      mvc.perform(get("/actuator/metrics").with(user("u").roles("USER"))).andExpect(status().isForbidden());
      mvc.perform(get("/actuator/health")).andExpect(status().isOk());
      mvc.perform(post("/api/orders").with(user("u").roles("USER"))).andExpect(status().isForbidden());
    });
  }
  @Test void registrationHashesPasswordAndPersistsOnlyOnSubscription() {
    UserRepository users=mock(UserRepository.class);when(users.findByUsername(anyString())).thenReturn(Mono.empty());when(users.findByEmail(anyString())).thenReturn(Mono.empty());
    when(users.save(any())).thenAnswer(c->Mono.just(c.getArgument(0)));
    org.springframework.security.crypto.password.PasswordEncoder encoder=PasswordEncoderFactories.createDelegatingPasswordEncoder();
    RegistrationForm form=new RegistrationForm();form.setUsername("Alice");form.setEmail("ALICE@example.test");form.setPassword("synthetic-password");
    Mono<User> result=new RegistrationService(users,encoder).register(form);verify(users,never()).save(any());
    StepVerifier.create(result).assertNext(u->{assertTrue(u.getPassword().startsWith("{bcrypt}"));assertTrue(encoder.matches(form.getPassword(),u.getPassword()));assertEquals("alice",u.getUsername());}).verifyComplete();
    verify(users,times(1)).save(any());
  }
  @Test void uniqueIndexRaceReturnsConflict() {
    UserRepository users=mock(UserRepository.class);when(users.findByUsername(anyString())).thenReturn(Mono.empty());when(users.findByEmail(anyString())).thenReturn(Mono.empty());
    when(users.save(any())).thenReturn(Mono.error(new org.springframework.dao.DuplicateKeyException("driver-secret")));
    RegistrationForm form=new RegistrationForm();form.setUsername("alice");form.setEmail("alice@example.test");form.setPassword("synthetic-password");
    StepVerifier.create(new RegistrationService(users,PasswordEncoderFactories.createDelegatingPasswordEncoder()).register(form))
      .expectErrorMatches(e->e instanceof org.springframework.web.server.ResponseStatusException && ((org.springframework.web.server.ResponseStatusException)e).getRawStatusCode()==409 && !e.getMessage().contains("driver-secret")).verify();
  }
}
