package tacos.messaging;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import java.util.concurrent.CompletionStage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
@Service @ConditionalOnProperty(name="tacocloud.messaging.transport",havingValue="rabbit")
public class RabbitOrderMessagingService implements OrderMessagingService {
  @Value("${tacocloud.messaging.destination:tacocloud.orders}") private String destination;
  private final RabbitTemplate template;
  public RabbitOrderMessagingService(RabbitTemplate template) { this.template=template; }
  public CompletionStage<Void> sendOrder(OrderEvent event) { java.util.concurrent.CompletableFuture<Void> result=new java.util.concurrent.CompletableFuture<>();
    try {
      byte[] json=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(event);
      org.springframework.amqp.core.MessageProperties properties=new org.springframework.amqp.core.MessageProperties();
      properties.setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);properties.setHeader("X-Correlation-Id",event.getCorrelationId());
      properties.setContentType("application/json");properties.setCorrelationId(event.getCorrelationId());
      org.springframework.amqp.rabbit.connection.CorrelationData correlation=new org.springframework.amqp.rabbit.connection.CorrelationData(event.getEventId());
      template.send("",destination,new org.springframework.amqp.core.Message(json,properties),correlation);
      correlation.getFuture().addCallback(confirm->{
        if(confirm.isAck() && correlation.getReturned()==null) result.complete(null);
        else result.completeExceptionally(new IllegalStateException("BROKER_REJECTED"));
      },result::completeExceptionally);
    } catch(Exception e) { result.completeExceptionally(e); } return result; }
}
