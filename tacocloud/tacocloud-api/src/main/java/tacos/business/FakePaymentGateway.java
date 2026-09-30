package tacos.business;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.util.UUID;
import tacos.PaymentMethod;
import tacos.web.api.ApiException;
@Component
public class FakePaymentGateway implements PaymentGateway {
  public Mono<PaymentMethod> tokenize(String card,String userId) {
    return Mono.fromSupplier(()->{
      if(card==null || !card.matches("test_[0-9]{4}")) throw ApiException.invalid("SYNTHETIC_CARD_REQUIRED");
      PaymentMethod p=new PaymentMethod();p.setUserId(userId);p.setBrand("LAB");p.setLast4(card.substring(5));
      p.setPaymentToken("test_token_"+UUID.randomUUID());return p;
    });
  }
}
