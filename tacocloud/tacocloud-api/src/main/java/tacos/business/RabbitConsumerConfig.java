package tacos.business;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import reactor.util.retry.Retry;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import tacos.messaging.OrderEvent;
import tacos.security.Correlation;
@Configuration @EnableRabbit @ConditionalOnProperty(name="tacocloud.messaging.transport",havingValue="rabbit")
public class RabbitConsumerConfig {
  @Bean public Declarables queues(@Value("${tacocloud.messaging.destination:tacocloud.orders}") String destination) {
    return new Declarables(new org.springframework.amqp.core.Queue(destination,true),new org.springframework.amqp.core.Queue(destination+".dlq",true));
  }
  @Bean public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory factory) {
    SimpleRabbitListenerContainerFactory f=new SimpleRabbitListenerContainerFactory();f.setConnectionFactory(factory);
    f.setAcknowledgeMode(AcknowledgeMode.MANUAL);f.setDefaultRequeueRejected(false);f.setPrefetchCount(1);return f;
  }
  @Bean public RabbitListener listener(IdempotentEventConsumer consumer,DeadLetterService deadLetters,RabbitTemplate rabbit,
      @Value("${tacocloud.messaging.destination:tacocloud.orders}") String destination,
      @Value("${tacocloud.consumer.max-retries:3}") long retries) {
    return new RabbitListener(consumer,deadLetters,rabbit,destination,retries);
  }
  @Bean("broker") public org.springframework.boot.actuate.health.ReactiveHealthIndicator brokerHealth(ConnectionFactory factory) {
    return ()->reactor.core.publisher.Mono.fromCallable(()->{
      try(Connection connection=factory.createConnection()) { return org.springframework.boot.actuate.health.Health.up().build(); }
    }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic()).timeout(Duration.ofSeconds(3))
      .onErrorReturn(org.springframework.boot.actuate.health.Health.down().withDetail("reason","BROKER_UNAVAILABLE").build());
  }
  public static class RabbitListener {
    private final IdempotentEventConsumer consumer;private final DeadLetterService deadLetters;private final RabbitTemplate rabbit;private final String destination;private final long retries;
    RabbitListener(IdempotentEventConsumer consumer,DeadLetterService deadLetters,RabbitTemplate rabbit,String destination,long retries) {
      this.consumer=consumer;this.deadLetters=deadLetters;this.rabbit=rabbit;this.destination=destination;this.retries=retries;
    }
    @org.springframework.amqp.rabbit.annotation.RabbitListener(queues="${tacocloud.messaging.destination:tacocloud.orders}")
    public void receive(Message message,com.rabbitmq.client.Channel channel) throws Exception {
      long tag=message.getMessageProperties().getDeliveryTag();OrderEvent event=null;
      String correlation=Correlation.validOrNew(message.getMessageProperties().getCorrelationId());
      try {
        try {event=new com.fasterxml.jackson.databind.ObjectMapper().readValue(message.getBody(),OrderEvent.class);}
        catch(Exception invalid){throw new IdempotentEventConsumer.PermanentEventException("INVALID_JSON");}
        correlation=Correlation.validOrNew(event.getCorrelationId());
        // El borde AMQP espera el commit antes del ack; los servicios permanecen reactivos.
        consumer.consume(event).retryWhen(Retry.backoff(retries,Duration.ofMillis(50)).maxBackoff(Duration.ofMillis(500))
          .filter(TransactionRetry::transientFailure)).block(Duration.ofSeconds(30));
      } catch(Exception error) {
        String cause=error instanceof IdempotentEventConsumer.PermanentEventException?"INVALID_EVENT":"RETRY_EXHAUSTED";
        try {
          DeadLetterRecord record=deadLetters.store(message.getBody(),event,correlation,cause).block(Duration.ofSeconds(10));
          Map<String,Object> summary=new LinkedHashMap<>();summary.put("id",record.getId());summary.put("cause",cause);summary.put("correlationId",record.getCorrelationId());summary.put("replayable",record.getEvent()!=null);
          MessageProperties props=new MessageProperties();props.setContentType("application/json");props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
          props.setCorrelationId(record.getCorrelationId());props.setHeader("x-failure-cause",cause);props.setHeader("X-Correlation-Id",record.getCorrelationId());
          CorrelationData confirm=new CorrelationData(record.getId());
          rabbit.send("",destination+".dlq",new Message(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(summary),props),confirm);
          if(!confirm.getFuture().get(10,TimeUnit.SECONDS).isAck() || confirm.getReturned()!=null)throw new IllegalStateException("DLQ_UNCONFIRMED");
        } catch(Exception unavailable) {
          channel.basicNack(tag,false,true);return;
        }
      }
      channel.basicAck(tag,false);
    }
  }
}
