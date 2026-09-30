package tacos.business;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import tacos.messaging.OrderEventPayload;
@Data @Document
public class KitchenTicket {
  @Id private String id;
  private long orderVersion=-1;
  private OrderEventPayload payload;
}
