package tacos.messaging;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
@Data @JsonIgnoreProperties(ignoreUnknown=true)
public class OrderEvent {
  private String eventId;
  private OrderEventType eventType;
  private int version=1;
  private String occurredAt;
  private String correlationId;
  private OrderEventPayload payload;
}
