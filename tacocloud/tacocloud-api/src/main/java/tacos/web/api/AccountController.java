package tacos.web.api;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Mono;
import java.util.*;
import tacos.*;
import tacos.data.PaymentMethodRepository;
import tacos.business.*;
import tacos.api.dto.*;
@RestController
public class AccountController {
  private final IdentityService identities;private final SocialService social;private final CatalogService catalog;
  private final ReactiveMongoTemplate mongo;private final OrderApplicationService orders;private final PaymentGateway gateway;private final PaymentMethodRepository payments;
  public AccountController(IdentityService identities,SocialService social,CatalogService catalog,ReactiveMongoTemplate mongo,OrderApplicationService orders,PaymentGateway gateway,PaymentMethodRepository payments) {
    this.identities=identities;this.social=social;this.catalog=catalog;this.mongo=mongo;this.orders=orders;this.gateway=gateway;this.payments=payments;
  }
  @PutMapping({"/api/users/me/favorites/{id}","/api/v1/users/me/favorites/{id}"}) @ResponseStatus(HttpStatus.NO_CONTENT) public Mono<Void> favorite(@PathVariable String id,Authentication auth) { return social.favorite(id,auth); }
  @DeleteMapping({"/api/users/me/favorites/{id}","/api/v1/users/me/favorites/{id}"}) @ResponseStatus(HttpStatus.NO_CONTENT) public Mono<Void> unfavorite(@PathVariable String id,Authentication auth) { return social.unfavorite(id,auth); }
  @GetMapping({"/api/users/me/favorites","/api/v1/users/me/favorites"}) public Mono<Responses.Page<Responses.TacoResponse>> favorites(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,Authentication auth) { return social.favorites(page,size,auth); }
  @GetMapping({"/api/users/me/orders","/api/v1/users/me/orders"}) public Mono<Responses.Page<Responses.OrderResponse>> history(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,Authentication auth) {
    return identities.user(auth).flatMap(user->historyQuery(Query.query(Criteria.where("user.id").is(user.getId())),page,size));
  }
  @GetMapping({"/api/users/me/orders/{id}","/api/v1/users/me/orders/{id}"}) public Mono<Responses.OrderResponse> detail(@PathVariable String id,Authentication auth) { return orders.owned(id,auth,false).map(ApiMapper::order); }
  @GetMapping({"/api/admin/orders","/api/v1/admin/orders"}) public Mono<Responses.Page<Responses.OrderResponse>> all(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
      @RequestParam(required=false) TacoOrder.Status status,@RequestParam(required=false) String userId) {
    Query q=new Query();if(status!=null)q.addCriteria(Criteria.where("status").is(status));if(userId!=null)q.addCriteria(Criteria.where("user.id").is(userId));return historyQuery(q,page,size);
  }
  private Mono<Responses.Page<Responses.OrderResponse>> historyQuery(Query q,int page,int size) {
    catalog.pageBounds(page,size);return Mono.zip(mongo.count(Query.of(q),TacoOrder.class),
      mongo.find(q.with(Sort.by(Sort.Order.desc("placedAt"),Sort.Order.asc("_id"))).skip((long)page*size).limit(size),TacoOrder.class).map(ApiMapper::order).collectList())
      .map(pair->new Responses.Page<>(pair.getT2(),page,size,pair.getT1()));
  }
  @PostMapping({"/api/payment-methods/tokenize","/api/v1/payment-methods/tokenize"}) @ResponseStatus(HttpStatus.CREATED) public Mono<Map<String,String>> tokenize(@Valid @RequestBody Requests.Tokenize request,Authentication auth) {
    return identities.user(auth).flatMap(user->gateway.tokenize(request.getSyntheticCard(),user.getId())).flatMap(payments::save).map(payment->{
      Map<String,String> response=new LinkedHashMap<>();response.put("id",payment.getId());response.put("brand",payment.getBrand());response.put("last4",payment.getLast4());return response;
    });
  }
}
