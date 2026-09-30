package tacos.business;
import lombok.Data;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import tacos.messaging.OrderEvent;
@Data @Document
public class DeadLetterRecord {
  @Id private String id;
  private String cause;
  private String correlationId;
  private Instant failedAt;
  private Instant replayedAt;
  private boolean replayed;
  private OrderEvent event;
}
