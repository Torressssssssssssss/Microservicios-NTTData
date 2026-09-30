package tacos.business;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import tacos.User;
import tacos.data.UserRepository;
import tacos.web.api.ApiException;
@Service
public class IdentityService {
  private final UserRepository users;
  public IdentityService(UserRepository users) { this.users=users; }
  public static boolean role(Authentication a,String role) {
    return a!=null && a.isAuthenticated() && a.getAuthorities().stream().anyMatch(x->x.getAuthority().equals("ROLE_"+role));
  }
  public Mono<User> user(Authentication a) {
    if(a==null || !a.isAuthenticated() || a instanceof AnonymousAuthenticationToken) return Mono.error(new ApiException(HttpStatus.UNAUTHORIZED,"AUTHENTICATION_REQUIRED"));
    return users.findByUsername(a.getName()).switchIfEmpty(Mono.error(new ApiException(HttpStatus.UNAUTHORIZED,"AUTHENTICATION_REQUIRED")));
  }
}
