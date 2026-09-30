package tacos.business;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tacos.messaging.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
class TransportTest {
  ApplicationContextRunner runner() {
    return new ApplicationContextRunner().withPropertyValues("spring.profiles.active=test")
      .withBean(JmsTemplate.class,()->mock(JmsTemplate.class)).withBean(RabbitTemplate.class,()->mock(RabbitTemplate.class))
      .withBean(KafkaTemplate.class,()->mock(KafkaTemplate.class))
      .withUserConfiguration(NoOpOrderMessagingService.class,JmsOrderMessagingService.class,RabbitOrderMessagingService.class,KafkaOrderMessagingService.class,TransportGuard.class);
  }
  @Test void exactlyOneAdapterForEveryTransport() {
    for(String t:new String[]{"noop","jms","rabbit","kafka"})runner().withPropertyValues("tacocloud.messaging.transport="+t)
      .run(c->{assertThat(c).hasNotFailed();assertThat(c).hasSingleBean(OrderMessagingService.class);});
  }
  @Test void invalidValueAndProductionNoopFail() {
    runner().withPropertyValues("tacocloud.messaging.transport=unknown").run(c->assertThat(c).hasFailed());
    runner().withPropertyValues("tacocloud.messaging.transport=noop","spring.profiles.active=prod").run(c->assertThat(c).hasFailed());
  }
}
