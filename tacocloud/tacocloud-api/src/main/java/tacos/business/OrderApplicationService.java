package tacos.business;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import tacos.*;
import tacos.data.*;
import tacos.api.dto.Requests.*;
import tacos.web.api.*;
import tacos.messaging.OrderEventType;

@Service
public class OrderApplicationService {
  @org.springframework.beans.factory.annotation.Autowired(required=false) private BusinessMetrics metrics;
  private final OrderRepository orders;private final PaymentMethodRepository payments;private final IdentityService identities;
  private final PricingService pricing;private final InventoryService inventory;private final OutboxService outbox;
  private final TransactionalOperator tx;private final Clock clock;private final ReactiveMongoTemplate mongo;
  public OrderApplicationService(OrderRepository orders,PaymentMethodRepository payments,IdentityService identities,PricingService pricing,
      InventoryService inventory,OutboxService outbox,TransactionalOperator tx,Clock clock,ReactiveMongoTemplate mongo) {
    this.orders=orders;this.payments=payments;this.identities=identities;this.pricing=pricing;this.inventory=inventory;
    this.outbox=outbox;this.tx=tx;this.clock=clock;this.mongo=mongo;
  }
  public Mono<TacoOrder> quote(OrderCreateRequest request) { return pricing.quote(request); }
  public Mono<TacoOrder> place(OrderCreateRequest request,Authentication auth) {
    return identities.user(auth).flatMap(user->placeAs(request,user)).doOnError(e->{if(metrics!=null)metrics.failed(e);});
  }
  public Mono<TacoOrder> placeAs(OrderCreateRequest request,User user) {
    return tx.transactional(draft(request,user).flatMap(this::persist));
  }
  private Mono<TacoOrder> draft(OrderCreateRequest request,User user) {
    return pricing.quote(request).flatMap(order->payments.findById(request.getPaymentMethodId())
      .filter(payment->user.getId().equals(payment.getUserId()) && payment.getPaymentToken()!=null)
      .switchIfEmpty(Mono.error(ApiException.missing("PAYMENT_METHOD"))).map(payment->{
        order.setId(UUID.randomUUID().toString());order.setPlacedAt(Date.from(clock.instant()));order.setUser(user);
        order.setPaymentMethodId(payment.getId());order.setPaymentBrand(payment.getBrand());order.setPaymentLast4(payment.getLast4());
        order.getHistory().add(new StatusChange(null,TacoOrder.Status.CREATED,user.getUsername(),clock.instant(),"API","ORDER_CREATED"));return order;
      }));
  }
  private Mono<TacoOrder> persist(TacoOrder order) {
    long started=System.nanoTime();
    return inventory.reserve(order).then(orders.save(order)).flatMap(saved->outbox.append(saved,OrderEventType.ORDER_CREATED))
      .flatMap(saved->metrics==null?Mono.just(saved):metrics.created(saved,started).thenReturn(saved));
  }
  public Mono<TacoOrder> owned(String id,Authentication auth,boolean admin) {
    return identities.user(auth).flatMap(user->orders.findById(id).switchIfEmpty(Mono.error(ApiException.missing("ORDER")))
      .flatMap(order->(order.getUser()!=null && user.getId().equals(order.getUser().getId())) || (admin && IdentityService.role(auth,"ADMIN"))
        ? Mono.just(order) : Mono.error(new ApiException(HttpStatus.FORBIDDEN,"ORDER_ACCESS_DENIED"))));
  }
  public OrderCreateRequest requestFrom(TacoOrder old,String paymentId) {
    OrderCreateRequest r=new OrderCreateRequest();r.setDeliveryName(old.getDeliveryName());r.setDeliveryStreet(old.getDeliveryStreet());
    r.setDeliveryCity(old.getDeliveryCity());r.setDeliveryState(old.getDeliveryState());r.setDeliveryZip(old.getDeliveryZip());
    r.setPaymentMethodId(paymentId);r.setCouponCode(old.getCouponCode());
    List<Item> items=new ArrayList<>();
    if(!old.getItems().isEmpty()) old.getItems().forEach(line->items.add(item(line.getTaco(),line.getQuantity())));
    else old.getTacos().forEach(taco->items.add(item(taco,1)));
    r.setItems(items);return r;
  }
  private Item item(Taco taco,int quantity) {
    Design design=new Design();design.setName(taco.getName());design.setBeverage(taco.isBeverage());design.setVegan(taco.isVeganRequested());design.setIngredientIds(taco.getIngredients().stream().map(Ingredient::getId).collect(Collectors.toList()));
    Item item=new Item();item.setTaco(design);item.setQuantity(quantity);return item;
  }
  public Mono<TacoOrder> fromEmail(EmailOrderService converter,EmailOrder email) {
    return converter.convertEmailOrderToDomainOrder(Mono.just(email)).flatMap(order->placeAs(requestFrom(order,order.getPaymentMethodId()),order.getUser()));
  }
  public Mono<TacoOrder> reorder(String id,Reorder request,String key,Authentication auth) {
    if(key==null || !key.matches("[A-Za-z0-9_-]{8,100}")) return Mono.error(new ApiException(HttpStatus.BAD_REQUEST,"IDEMPOTENCY_KEY_REQUIRED"));
    String fingerprint=id+"|"+request.getPaymentMethodId()+"|"+request.isConfirmPriceChange();
    return identities.user(auth).flatMap(user->{
      Query q=Query.query(Criteria.where("userId").is(user.getId()).and("key").is(key));
      Mono<TacoOrder> repeated=mongo.findOne(q,ReorderRecord.class).flatMap(record->{
        if(!fingerprint.equals(record.getRequestHash())) return Mono.error(ApiException.conflict("IDEMPOTENCY_CONFLICT"));
        return orders.findById(record.getOrderId()).switchIfEmpty(Mono.error(ApiException.conflict("REORDER_INCOMPLETE")));
      });
      return repeated.switchIfEmpty(Mono.defer(()->tx.transactional(owned(id,auth,false).flatMap(old->
        draft(requestFrom(old,request.getPaymentMethodId()),user).flatMap(fresh->{
          if(fresh.getTotal().compareTo(old.getTotal())!=0 && !request.isConfirmPriceChange())
            return Mono.error(new PriceChangedException(old.getTotal(),tacos.api.dto.ApiMapper.order(fresh)));
          ReorderRecord record=new ReorderRecord();record.setId(UUID.randomUUID().toString());record.setUserId(user.getId());record.setKey(key);
          record.setRequestHash(fingerprint);record.setOrderId(fresh.getId());
          return mongo.insert(record).then(persist(fresh));
        }))))).onErrorResume(org.springframework.dao.DuplicateKeyException.class,e->repeated.switchIfEmpty(Mono.error(e)))
        .retryWhen(TransactionRetry.conflicts());
    });
  }
  public Mono<TacoOrder> replace(String id,Replacement request,Authentication auth) {
    if(request.getId()!=null && !id.equals(request.getId())) return Mono.error(new ApiException(HttpStatus.BAD_REQUEST,"INCONSISTENT_ID"));
    return tx.transactional(owned(id,auth,true).flatMap(old->{
      if(old.getStatus()!=TacoOrder.Status.CREATED) return Mono.error(ApiException.conflict("ORDER_NOT_EDITABLE"));
      if(!Objects.equals(old.getPaymentMethodId(),request.getPaymentMethodId())) return Mono.error(new ApiException(HttpStatus.BAD_REQUEST,"PAYMENT_IMMUTABLE"));
      return inventory.release(old.getId()).then(
        mongo.remove(Query.query(Criteria.where("_id").is(old.getId())),InventoryReservation.class))
        .then(pricing.quote(request)).flatMap(fresh->{
          old.setItems(fresh.getItems());old.setSubtotal(fresh.getSubtotal());old.setDiscount(fresh.getDiscount());old.setTotal(fresh.getTotal());old.setCouponCode(fresh.getCouponCode());
          old.setDeliveryName(fresh.getDeliveryName());old.setDeliveryStreet(fresh.getDeliveryStreet());old.setDeliveryCity(fresh.getDeliveryCity());
          old.setDeliveryState(fresh.getDeliveryState());old.setDeliveryZip(fresh.getDeliveryZip());
          return inventory.reserve(old).then(orders.save(old)).flatMap(saved->outbox.append(saved,OrderEventType.STATUS_CHANGED));
        });
    }));
  }
  public Mono<TacoOrder> patch(String id,OrderPatchRequest patch,Authentication auth) {
    return tx.transactional(owned(id,auth,true).flatMap(order->{
      if(order.getStatus()!=TacoOrder.Status.CREATED) return Mono.error(ApiException.conflict("ORDER_NOT_EDITABLE"));
      if(patch.getDeliveryName()!=null) order.setDeliveryName(patch.getDeliveryName());
      if(patch.getDeliveryStreet()!=null) order.setDeliveryStreet(patch.getDeliveryStreet());
      if(patch.getDeliveryCity()!=null) order.setDeliveryCity(patch.getDeliveryCity());
      if(patch.getDeliveryState()!=null) order.setDeliveryState(patch.getDeliveryState());
      if(patch.getDeliveryZip()!=null) order.setDeliveryZip(patch.getDeliveryZip());
      for(String v:Arrays.asList(order.getDeliveryName(),order.getDeliveryStreet(),order.getDeliveryCity(),order.getDeliveryState(),order.getDeliveryZip()))
        if(v==null || v.trim().isEmpty() || v.length()>200) return Mono.error(new ApiException(HttpStatus.BAD_REQUEST,"INVALID_DELIVERY"));
      if(order.getDeliveryName().length()>100 || order.getDeliveryCity().length()>100 || order.getDeliveryState().length()>100
          || !order.getDeliveryZip().matches("[A-Za-z0-9 -]{3,12}"))return Mono.error(new ApiException(HttpStatus.BAD_REQUEST,"INVALID_DELIVERY"));
      return orders.save(order);
    }));
  }
}
