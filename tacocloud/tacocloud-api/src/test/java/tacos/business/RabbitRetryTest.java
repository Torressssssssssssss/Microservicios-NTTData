package tacos.business;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import reactor.core.publisher.Mono;
import java.util.concurrent.atomic.AtomicInteger;
import tacos.messaging.*;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
class RabbitRetryTest {
  OrderEvent event() { tacos.TacoOrder order=new tacos.TacoOrder();order.setId("order");return new EventFactory(java.time.Clock.systemUTC()).create(order,OrderEventType.ORDER_CREATED); }
  Message message(OrderEvent event)throws Exception {
    MessageProperties properties=new MessageProperties();properties.setDeliveryTag(7);properties.setCorrelationId(event.getCorrelationId());
    return new Message(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(event),properties);
  }
  @Test void transientErrorsRetryThenAckOnlyAfterSuccess()throws Exception {
    IdempotentEventConsumer consumer=mock(IdempotentEventConsumer.class);AtomicInteger attempts=new AtomicInteger();
    when(consumer.consume(any())).thenReturn(Mono.defer(()->attempts.incrementAndGet()<3?Mono.error(new org.springframework.dao.TransientDataAccessResourceException("temporary")):Mono.just(true)));
    DeadLetterService dlq=mock(DeadLetterService.class);com.rabbitmq.client.Channel channel=mock(com.rabbitmq.client.Channel.class);
    new RabbitConsumerConfig.RabbitListener(consumer,dlq,mock(RabbitTemplate.class),"queue",3).receive(message(event()),channel);
    assertEquals(3,attempts.get());verify(channel).basicAck(7,false);verifyNoInteractions(dlq);verify(channel,never()).basicNack(anyLong(),anyBoolean(),anyBoolean());
  }
  @Test void permanentErrorHasNoRetriesAndAckFollowsConfirmedSanitizedDlq()throws Exception {
    checkDeadLetter(false,1);
  }
  @Test void transientExhaustionHasExactConfiguredRetryLimit()throws Exception {
    checkDeadLetter(true,3);
  }
  void checkDeadLetter(boolean transientError,int expected)throws Exception {
    IdempotentEventConsumer consumer=mock(IdempotentEventConsumer.class);AtomicInteger attempts=new AtomicInteger();
    when(consumer.consume(any())).thenReturn(Mono.defer(()->{attempts.incrementAndGet();return Mono.error(transientError?new org.springframework.dao.TransientDataAccessResourceException("temporary"):new IdempotentEventConsumer.PermanentEventException("INVALID"));}));
    DeadLetterService dlq=mock(DeadLetterService.class);DeadLetterRecord row=new DeadLetterRecord();row.setId("safe-id");row.setCorrelationId("safe-correlation");
    when(dlq.store(any(),any(),anyString(),anyString())).thenReturn(Mono.just(row));RabbitTemplate rabbit=mock(RabbitTemplate.class);
    doAnswer(call->{CorrelationData data=call.getArgument(3);data.getFuture().set(new CorrelationData.Confirm(true,null));return null;})
      .when(rabbit).send(eq(""),eq("queue.dlq"),any(Message.class),any(CorrelationData.class));
    com.rabbitmq.client.Channel channel=mock(com.rabbitmq.client.Channel.class);
    new RabbitConsumerConfig.RabbitListener(consumer,dlq,rabbit,"queue",2).receive(message(event()),channel);
    assertEquals(expected,attempts.get());org.mockito.InOrder ordered=inOrder(dlq,rabbit,channel);
    ordered.verify(dlq).store(any(),any(),anyString(),eq(transientError?"RETRY_EXHAUSTED":"INVALID_EVENT"));
    ordered.verify(rabbit).send(eq(""),eq("queue.dlq"),any(Message.class),any(CorrelationData.class));ordered.verify(channel).basicAck(7,false);
  }
}
