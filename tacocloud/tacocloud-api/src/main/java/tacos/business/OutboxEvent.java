package tacos.business;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import tacos.messaging.OrderEvent;
@Data @Document
public class OutboxEvent {
  @Id private String id;
  private OrderEvent payload;
  private String state="NEW";
  private int attempts;
  private Instant createdAt;
  private Instant publishedAt;
  private Instant nextAttempt;
  private Instant leaseUntil;
  private String claimToken;
  private String lastError;
}
