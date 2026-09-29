package tacos.web.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.Valid;
import java.util.List;
import tacos.Taco;

@Data
public class OrderReplaceRequest {
  @NotBlank
  private String deliveryName;
  @NotBlank
  private String deliveryStreet;
  @NotBlank
  private String deliveryCity;
  @NotBlank
  private String deliveryState;
  @NotBlank
  private String deliveryZip;
  private String id;
  @NotEmpty
  @Valid
  private List<Taco> tacos;

  @JsonAnySetter
  public void rejectUnknown(String field, Object value) {
    throw new IllegalArgumentException("Field is not allowed: " + field);
  }
}
