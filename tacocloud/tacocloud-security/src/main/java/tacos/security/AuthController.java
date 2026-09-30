package tacos.security;

import java.util.*;
import javax.servlet.http.*;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.http.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.security.core.context.*;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import tacos.User;
import tacos.data.UserRepository;

@RestController
@RequestMapping({"/api/auth","/api/v1/auth"})
public class AuthController {
  private final AuthenticationManager authenticationManager;
  private final RegistrationService registration;
  public AuthController(AuthenticationManager authenticationManager,RegistrationService registration) {
    this.authenticationManager=authenticationManager;this.registration=registration;
  }
  @GetMapping("/csrf")
  public Map<String,String> csrf(org.springframework.security.web.csrf.CsrfToken token) {
    Map<String,String> result=new LinkedHashMap<>();result.put("token",token.getToken());result.put("headerName",token.getHeaderName());return result;
  }
  @PostMapping("/register")
  public Mono<ResponseEntity<Map<String,Object>>> register(@Valid @RequestBody RegistrationForm form) {
    return registration.register(form).map(user->ResponseEntity.status(HttpStatus.CREATED).body(account(user)));
  }

  @PostMapping("/login")
  public Map<String,Object> login(@Valid @RequestBody LoginRequest request,HttpServletRequest http) {
    try {
      Authentication authentication=authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
          request.getUsername().trim().toLowerCase(Locale.ROOT),request.getPassword()));
      SecurityContext context=SecurityContextHolder.createEmptyContext();context.setAuthentication(authentication);
      SecurityContextHolder.setContext(context);
      if(http.getSession(false)!=null)http.changeSessionId();
      http.getSession(true).setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,context);
      return account(authentication);
    } catch(AuthenticationException error) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"INVALID_CREDENTIALS");
    }
  }

  @GetMapping("/me")
  public Map<String,Object> me(Authentication authentication) {
    if(authentication==null || !authentication.isAuthenticated()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"AUTHENTICATION_REQUIRED");
    }
    return account(authentication);
  }

  private Map<String,Object> account(Authentication authentication) {
    Map<String,Object> result=new LinkedHashMap<>();result.put("username",authentication.getName());
    List<String> roles=new ArrayList<>();authentication.getAuthorities().forEach(role->roles.add(role.getAuthority()));
    result.put("roles",roles);return result;
  }

  private Map<String,Object> account(User user) {
    Map<String,Object> result=new LinkedHashMap<>();result.put("id",user.getId());result.put("username",user.getUsername());
    result.put("email",user.getEmail());return result;
  }

  @Data
  public static class LoginRequest {
    @NotBlank private String username;
    @NotBlank private String password;
  }
}
