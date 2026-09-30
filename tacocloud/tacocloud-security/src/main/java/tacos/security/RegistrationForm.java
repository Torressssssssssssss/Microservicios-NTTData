package tacos.security;
import org.springframework.security.crypto.password.PasswordEncoder;

import lombok.Data;
import tacos.User;

@Data
public class RegistrationForm {

  @javax.validation.constraints.NotBlank
  @javax.validation.constraints.Pattern(regexp="[a-zA-Z0-9_.-]{3,40}")
  private String username;
  @javax.validation.constraints.Size(min=10,max=72)
  @javax.validation.constraints.NotBlank
  @lombok.ToString.Exclude
  private String password;
  private String fullname;
  private String street;
  private String city;
  private String state;
  private String zip;
  private String phone;
  @javax.validation.constraints.Email
  @javax.validation.constraints.NotBlank
  private String email;
  
  public User toUser(PasswordEncoder passwordEncoder) {
    return new User(
        username, passwordEncoder.encode(password), 
        fullname, street, city, state, zip, phone, email);
  }
  
}
