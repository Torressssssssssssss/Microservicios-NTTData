package tacos.security;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DuplicateKeyException;
import reactor.core.publisher.Mono;
import tacos.User;
import tacos.data.UserRepository;
@Service
public class RegistrationService {
  private final UserRepository users; private final PasswordEncoder encoder;
  public RegistrationService(UserRepository users,PasswordEncoder encoder) { this.users=users;this.encoder=encoder; }
  public Mono<User> register(RegistrationForm form) {
    return Mono.defer(()->{
      form.setUsername(form.getUsername().trim().toLowerCase(java.util.Locale.ROOT));
      form.setEmail(form.getEmail().trim().toLowerCase(java.util.Locale.ROOT));
      return Mono.zip(users.findByUsername(form.getUsername()).hasElement(),users.findByEmail(form.getEmail()).hasElement())
        .flatMap(exists->{
          if(exists.getT1() || exists.getT2()) return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,"ACCOUNT_ALREADY_EXISTS"));
          return Mono.fromCallable(()->form.toUser(encoder)).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic()).flatMap(users::save);
        }).onErrorMap(DuplicateKeyException.class,e->new ResponseStatusException(HttpStatus.CONFLICT,"ACCOUNT_ALREADY_EXISTS"));
    });
  }
}
