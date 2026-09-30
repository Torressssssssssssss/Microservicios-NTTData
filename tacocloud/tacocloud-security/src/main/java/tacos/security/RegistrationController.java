package tacos.security;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;
import reactor.core.publisher.Mono;
@Controller @RequestMapping("/register")
public class RegistrationController {
  private final RegistrationService registration;
  public RegistrationController(RegistrationService registration) { this.registration=registration; }
  @GetMapping public String registerForm() { return "registration"; }
  @PostMapping public Mono<String> processRegistration(@Valid RegistrationForm form) {
    return registration.register(form).thenReturn("redirect:/login");
  }
}
