package tacos.security;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.*;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.*;
import java.util.*;

@Configuration @EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {
  @Autowired private UserDetailsService users;
  @Value("${taco.api.allowed-origin:http://localhost:8080}") private String origin;
  @Override protected void configure(HttpSecurity http) throws Exception {
    http.cors().and().authorizeRequests()
      .antMatchers(HttpMethod.OPTIONS,"/**").permitAll()
      .antMatchers("/api/auth/register","/api/v1/auth/register","/api/auth/login","/api/v1/auth/login","/api/auth/csrf","/api/v1/auth/csrf").permitAll()
      .antMatchers("/api/auth/me","/api/v1/auth/me").authenticated()
      .antMatchers("/data-api/**").denyAll()
      .antMatchers("/actuator/health","/actuator/health/liveness","/actuator/health/readiness").permitAll()
      .antMatchers("/actuator/**").hasRole("ADMIN")
      .antMatchers("/api/admin/**","/api/v1/admin/**").hasRole("ADMIN")
      .antMatchers("/api/kitchen/**","/api/v1/kitchen/**","/kitchen","/kitchen.html").hasRole("KITCHEN")
      .antMatchers(HttpMethod.GET,"/api/announcements","/api/v1/announcements").permitAll()
      .antMatchers(HttpMethod.GET,"/api/ingredients/**","/api/v1/ingredients/**","/api/tacos/**","/api/v1/tacos/**").permitAll()
      .antMatchers("/api/ingredients/**","/api/v1/ingredients/**").hasRole("ADMIN")
      .antMatchers(HttpMethod.POST,"/api/users","/api/v1/users").permitAll()
      .antMatchers("/api/orders/fromEmail","/api/v1/orders/fromEmail").hasRole("ADMIN")
      .antMatchers("/api/orders/**","/api/v1/orders/**","/api/users/me/**","/api/v1/users/me/**","/api/payment-methods/**","/api/v1/payment-methods/**").hasAnyRole("USER","ADMIN")
      .antMatchers(HttpMethod.POST,"/api/tacos","/api/v1/tacos","/api/tacos/validate","/api/v1/tacos/validate").hasAnyRole("USER","ADMIN")
      .antMatchers(HttpMethod.PUT,"/api/tacos/*/rating","/api/v1/tacos/*/rating").hasAnyRole("USER","ADMIN")
      .antMatchers("/","/login","/register","/index.html","/favicon.ico","/ui","/ui/**","/openapi.yaml","/*.js","/*.css","/assets/**").permitAll()
      .anyRequest().denyAll()
      .and().formLogin().loginPage("/login").permitAll()
      .and().httpBasic().realmName("Taco Cloud")
      .and().csrf().csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
      .and().exceptionHandling()
        .authenticationEntryPoint((request,response,error)->problem(request,response,401,"AUTHENTICATION_REQUIRED"))
        .accessDeniedHandler((request,response,error)->problem(request,response,403,"ACCESS_DENIED"));
  }
  private void problem(javax.servlet.http.HttpServletRequest request,javax.servlet.http.HttpServletResponse response,int status,String code) throws java.io.IOException {
    response.setStatus(status);response.setContentType("application/problem+json");
    Map<String,Object> body=new LinkedHashMap<>();body.put("type","urn:tacocloud:problem:"+code);
    body.put("title",code);body.put("status",status);body.put("detail",code);body.put("instance",request.getRequestURI());
    body.put("code",code);body.put("correlationId",request.getAttribute(Correlation.KEY));body.put("violations",Collections.emptyList());
    new com.fasterxml.jackson.databind.ObjectMapper().writeValue(response.getWriter(),new ApiProblem(body));
  }
  @Bean @Override public org.springframework.security.authentication.AuthenticationManager authenticationManagerBean() throws Exception { return super.authenticationManagerBean(); }
  @Bean public PasswordEncoder encoder() { return PasswordEncoderFactories.createDelegatingPasswordEncoder(); }
  @Bean public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration c=new CorsConfiguration();c.setAllowedOrigins(Collections.singletonList(origin));
    c.setAllowedMethods(Arrays.asList("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
    c.setAllowedHeaders(Arrays.asList("Content-Type","Authorization","X-XSRF-TOKEN","Idempotency-Key","X-Correlation-Id"));
    c.setAllowCredentials(true);c.setExposedHeaders(Arrays.asList("Location","X-Correlation-Id","Deprecation","Link"));
    UrlBasedCorsConfigurationSource source=new UrlBasedCorsConfigurationSource();source.registerCorsConfiguration("/**",c);return source;
  }
  @Override protected void configure(AuthenticationManagerBuilder auth) throws Exception {
    auth.userDetailsService(users).passwordEncoder(encoder());
  }
}
