package tacos.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
  
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
      .authorizeHttpRequests(authorize -> authorize
        .requestMatchers(HttpMethod.OPTIONS).permitAll()
        .requestMatchers(HttpMethod.POST, "/api/ingredients").permitAll()
        .requestMatchers("/api/tacos/**", "/api/orders/**")
            .permitAll()
        .requestMatchers(HttpMethod.PATCH, "/api/ingredients").permitAll()
        .requestMatchers("/**").permitAll())
      .formLogin(form -> form.loginPage("/login"))
      .httpBasic(basic -> basic.realmName("Taco Cloud"))
      .logout(logout -> logout.logoutSuccessUrl("/"))
      .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**", "/api/**"))
      .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
    return http.build();
  }

  @Bean
  public PasswordEncoder encoder() {
//    return new StandardPasswordEncoder("53cr3t");
    return NoOpPasswordEncoder.getInstance();
  }
  
}
