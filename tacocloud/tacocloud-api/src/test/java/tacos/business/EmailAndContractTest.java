package tacos.business;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.time.*;
import reactor.core.publisher.*;
import reactor.test.StepVerifier;
import tacos.*;
import tacos.data.*;
import tacos.web.api.*;
import tacos.messaging.*;
import tacos.api.dto.*;
class EmailAndContractTest {
  User user() { User u=new User("alice","hash","Alice","Street","City","ST","12345","phone","a@test.test");u.setId("u");return u; }
  EmailOrder email() {EmailOrder e=new EmailOrder();e.setEmail("a@test.test");EmailOrder.EmailTaco t=new EmailOrder.EmailTaco();t.setName("Email taco");t.setIngredients(Arrays.asList("W","V"));e.setTacos(Arrays.asList(t,t));return e;}
  @Test void emailWaitsForAllLookupsAndSubscribesOnce() {
    UserRepository users=mock(UserRepository.class);PaymentMethodRepository payments=mock(PaymentMethodRepository.class);IngredientRepository ingredients=mock(IngredientRepository.class);
    PaymentMethod p=new PaymentMethod();p.setId("p");p.setUserId("u");
    when(users.findByEmail(anyString())).thenReturn(Mono.just(user()));when(payments.findByUserId("u")).thenReturn(Mono.just(p));
    when(ingredients.findById("W")).thenReturn(Mono.just(new Ingredient("W","Wrap",Ingredient.Type.WRAP)));
    when(ingredients.findById("V")).thenReturn(Mono.just(new Ingredient("V","Vegetable",Ingredient.Type.VEGGIES)));
    java.util.concurrent.atomic.AtomicInteger subscriptions=new java.util.concurrent.atomic.AtomicInteger();
    Mono<EmailOrder> cold=Mono.defer(()->{subscriptions.incrementAndGet();return Mono.just(email());});
    StepVerifier.create(new EmailOrderService(users,ingredients,payments).convertEmailOrderToDomainOrder(cold))
      .assertNext(order->{assertEquals(2,order.getTacos().size());assertEquals("V",order.getTacos().get(1).getIngredients().get(1).getId());}).verifyComplete();
    assertEquals(1,subscriptions.get());verify(users,times(1)).findByEmail(anyString());
  }
  @Test void missingUserPaymentAndIngredientAreControlled() {
    UserRepository users=mock(UserRepository.class);PaymentMethodRepository payments=mock(PaymentMethodRepository.class);IngredientRepository ingredients=mock(IngredientRepository.class);
    EmailOrderService service=new EmailOrderService(users,ingredients,payments);
    when(users.findByEmail(anyString())).thenReturn(Mono.empty());StepVerifier.create(service.convertEmailOrderToDomainOrder(Mono.just(email()))).expectErrorMatches(e->e.getMessage().contains("USER_NOT_FOUND")).verify();
    when(users.findByEmail(anyString())).thenReturn(Mono.just(user()));when(payments.findByUserId("u")).thenReturn(Mono.empty());StepVerifier.create(service.convertEmailOrderToDomainOrder(Mono.just(email()))).expectErrorMatches(e->e.getMessage().contains("PAYMENT_METHOD_NOT_FOUND")).verify();
    when(payments.findByUserId("u")).thenReturn(Mono.just(new PaymentMethod()));when(ingredients.findById("W")).thenReturn(Mono.empty());StepVerifier.create(service.convertEmailOrderToDomainOrder(Mono.just(email()))).expectErrorMatches(e->e.getMessage().contains("INGREDIENT_W_NOT_FOUND")).verify();
  }
  @Test void responseAndEventNeverSerializeSensitiveFieldsAndV1AcceptsExtensions() throws Exception {
    TacoOrder order=new TacoOrder();order.setId("o");order.setUser(user());order.setPaymentMethodId("private-payment");order.setVersion(1L);
    com.fasterxml.jackson.databind.ObjectMapper json=new com.fasterxml.jackson.databind.ObjectMapper();
    OrderEvent event=new EventFactory(Clock.systemUTC()).create(order,OrderEventType.ORDER_CREATED);
    String eventJson=json.writeValueAsString(event);String response=json.writeValueAsString(ApiMapper.order(order));
    for(String sensitive:Arrays.asList("password","authorities","ccNumber","ccCVV","paymentToken","private-payment","user")) {assertFalse(eventJson.contains(sensitive));assertFalse(response.contains(sensitive));}
    UUID.fromString(event.getEventId());assertNotNull(event.getCorrelationId());assertEquals(1,event.getVersion());
    OrderEvent read=json.readValue(eventJson.substring(0,eventJson.length()-1)+",\"futureField\":true}",OrderEvent.class);assertEquals(event.getEventId(),read.getEventId());
    IdempotentEventConsumer.validate(event);event.setVersion(99);assertThrows(IdempotentEventConsumer.PermanentEventException.class,()->IdempotentEventConsumer.validate(event));
  }
}
