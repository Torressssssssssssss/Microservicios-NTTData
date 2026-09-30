package tacos.messaging;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import java.util.concurrent.CompletionStage;
import org.springframework.kafka.core.KafkaTemplate;
@Service @ConditionalOnProperty(name="tacocloud.messaging.transport",havingValue="kafka")
public class KafkaOrderMessagingService implements OrderMessagingService {
  @Value("${tacocloud.messaging.destination:tacocloud.orders}") private String destination;
  private final KafkaTemplate<String,String> template;
  public KafkaOrderMessagingService(KafkaTemplate<String,String> template) { this.template=template; }
  public CompletionStage<Void> sendOrder(OrderEvent event) { java.util.concurrent.CompletableFuture<Void> result=new java.util.concurrent.CompletableFuture<>();
    try { template.send(new org.apache.kafka.clients.producer.ProducerRecord<String,String>(destination,null,event.getPayload().getOrderId(),new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(event),java.util.Collections.singletonList(new org.apache.kafka.common.header.internals.RecordHeader("X-Correlation-Id",event.getCorrelationId().getBytes(java.nio.charset.StandardCharsets.UTF_8)))))
      .addCallback(ok->result.complete(null),result::completeExceptionally); }
    catch(Exception e) { result.completeExceptionally(e); } return result; }
}
