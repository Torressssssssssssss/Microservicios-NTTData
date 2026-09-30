package tacos;
import java.util.Arrays;
import java.util.Collection;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.
                                          SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@Data
@NoArgsConstructor

@Document
public class User implements UserDetails {

  public User(String username,String password,String fullname,String street,String city,String state,String zip,String phoneNumber,String email) {
    this.username=username;this.password=password;this.fullname=fullname;this.street=street;this.city=city;this.state=state;this.zip=zip;this.phoneNumber=phoneNumber;this.email=email;
  }
  private static final long serialVersionUID = 1L;

  @Id
  private String id;
  
  @org.springframework.data.mongodb.core.index.Indexed(unique=true)
  private String username;
  
  @com.fasterxml.jackson.annotation.JsonIgnore
  @lombok.ToString.Exclude
  private String password;
  private String fullname;
  private String street;
  private String city;
  private String state;
  private String zip;
  private String phoneNumber;
  @org.springframework.data.mongodb.core.index.Indexed(unique=true)
  private String email;
  
  private java.util.Set<String> roles = new java.util.HashSet<>(Arrays.asList("USER"));
  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role))
        .collect(java.util.stream.Collectors.toList());
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

}
