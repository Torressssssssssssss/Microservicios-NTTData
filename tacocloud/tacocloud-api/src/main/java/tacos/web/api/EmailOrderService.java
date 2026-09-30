package tacos.web.api;
import org.springframework.stereotype.Service;
import reactor.core.publisher.*;
import tacos.*;
import tacos.data.*;
@Service
public class EmailOrderService {
  private final UserRepository users; private final IngredientRepository ingredients; private final PaymentMethodRepository payments;
  public EmailOrderService(UserRepository users,IngredientRepository ingredients,PaymentMethodRepository payments) {
    this.users=users;this.ingredients=ingredients;this.payments=payments;
  }
  public Mono<TacoOrder> convertEmailOrderToDomainOrder(Mono<EmailOrder> source) {
    return source.switchIfEmpty(Mono.error(ApiException.invalid("EMAIL_ORDER_REQUIRED"))).flatMap(email->{
      if(email.getEmail()==null || email.getTacos()==null || email.getTacos().isEmpty()) return Mono.error(ApiException.invalid("INVALID_EMAIL_ORDER"));
      return users.findByEmail(email.getEmail()).switchIfEmpty(Mono.error(ApiException.missing("USER"))).flatMap(user->
        payments.findByUserId(user.getId()).switchIfEmpty(Mono.error(ApiException.missing("PAYMENT_METHOD"))).flatMap(payment->
          Flux.fromIterable(email.getTacos()).concatMap(item->{
            if(item.getIngredients()==null || item.getName()==null) return Mono.error(ApiException.invalid("INVALID_EMAIL_TACO"));
            return Flux.fromIterable(item.getIngredients()).concatMap(id->ingredients.findById(id)
                .switchIfEmpty(Mono.error(ApiException.missing("INGREDIENT_"+id)))).collectList().map(values->{
                  Taco taco=new Taco();taco.setName(item.getName());taco.setIngredients(values);return taco;
                });
          }).collectList().map(tacos->{
            TacoOrder order=new TacoOrder();order.setUser(user);order.setPaymentMethodId(payment.getId());
            order.setPaymentBrand(payment.getBrand());order.setPaymentLast4(payment.getLast4());order.setTacos(tacos);
            order.setDeliveryName(user.getFullname());order.setDeliveryStreet(user.getStreet());order.setDeliveryCity(user.getCity());
            order.setDeliveryState(user.getState());order.setDeliveryZip(user.getZip());return order;
          })));
    });
  }
}
