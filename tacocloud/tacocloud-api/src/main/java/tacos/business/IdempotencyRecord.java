package tacos.business;
import lombok.Data;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import tacos.api.dto.Responses.OrderResponse;
@Data @Document
public class IdempotencyRecord {
  @Id private String id;
  private String userId;
  private String key;
  private String requestHash;
  private String orderId;
  private String status;
  private Instant createdAt;
  private Instant completedAt;
  private Instant expiresAt;
  private OrderResponse response;
}
