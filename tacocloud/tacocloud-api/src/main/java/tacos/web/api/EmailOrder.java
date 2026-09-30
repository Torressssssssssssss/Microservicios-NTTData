package tacos.web.api;

import java.util.List;

import lombok.Data;

@Data
public class EmailOrder extends tacos.api.dto.Requests.Strict {

  @javax.validation.constraints.NotBlank @javax.validation.constraints.Email private String email;
  @javax.validation.constraints.NotEmpty @javax.validation.constraints.Size(max=50) @javax.validation.Valid private List<EmailTaco> tacos;
  
  @Data
  public static class EmailTaco extends tacos.api.dto.Requests.Strict {
    @javax.validation.constraints.NotBlank @javax.validation.constraints.Size(min=5,max=100) private String name;
    @javax.validation.constraints.NotEmpty @javax.validation.constraints.Size(min=2,max=12) private List<@javax.validation.constraints.NotBlank String> ingredients;
  }
  
}
