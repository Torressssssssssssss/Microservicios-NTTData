package tacos;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.ToString;
@Data @Document
public class PaymentMethod {
  @Id private String id;
  private String userId;
  @JsonIgnore @ToString.Exclude private String paymentToken;
  private String brand;
  private String last4;
}
