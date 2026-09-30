package tacos.business;
import reactor.core.publisher.Mono;
import tacos.PaymentMethod;
public interface PaymentGateway { Mono<PaymentMethod> tokenize(String syntheticCard,String userId); }
