package tacos.security;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import reactor.core.publisher.Mono;
import java.util.*;
@RestController
public class UserRegistrationApi {
  private final RegistrationService registration;
  public UserRegistrationApi(RegistrationService registration) { this.registration=registration; }
  @PostMapping({"/api/users","/api/v1/users"}) public Mono<ResponseEntity<Map<String,String>>> register(@Valid @RequestBody RegistrationForm form) {
    return registration.register(form).map(user->{
      Map<String,String> body=new LinkedHashMap<>();body.put("id",user.getId());body.put("username",user.getUsername());body.put("email",user.getEmail());
      return ResponseEntity.status(HttpStatus.CREATED).body(body);
    });
  }
}
