package tacos.business;
import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import tacos.api.dto.*;
import tacos.web.api.ApiException;
@Service
public class IdempotentOrders {
  @org.springframework.beans.factory.annotation.Autowired(required=false) private BusinessMetrics metrics;
  private final ReactiveMongoTemplate mongo;private final TransactionalOperator tx;private final IdentityService identities;
  private final OrderApplicationService orders;private final Clock clock;private final long retentionHours;
  public IdempotentOrders(ReactiveMongoTemplate mongo,TransactionalOperator tx,IdentityService identities,OrderApplicationService orders,
      Clock clock,@Value("${tacocloud.idempotency.retention-hours:168}") long retentionHours) {
    this.mongo=mongo;this.tx=tx;this.identities=identities;this.orders=orders;this.clock=clock;this.retentionHours=retentionHours;
    if(retentionHours<24)throw new IllegalArgumentException("Idempotency retention must be at least 24 hours");
  }
  public static String fingerprint(Requests.OrderCreateRequest request) {
    try {
      ObjectMapper mapper=new ObjectMapper().configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY,true)
        .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,true);
      com.fasterxml.jackson.databind.node.ObjectNode tree=mapper.valueToTree(request);
      String coupon=request.getCouponCode();tree.put("couponCode",coupon==null || coupon.trim().isEmpty()?null:coupon.trim().toUpperCase(Locale.ROOT));
      byte[] digest=MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(tree));
      StringBuilder out=new StringBuilder();for(byte b:digest)out.append(String.format("%02x",b));return out.toString();
    } catch(Exception e) { throw new IllegalArgumentException("INVALID_REQUEST"); }
  }
  public Mono<Responses.OrderResponse> place(Requests.OrderCreateRequest request,String key,Authentication auth) {
    if(key==null || !key.matches("[A-Za-z0-9_-]{8,100}"))return Mono.error(new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,"INVALID_IDEMPOTENCY_KEY"));
    String hash=fingerprint(request);
    return identities.user(auth).flatMap(user->Mono.defer(()->{
      Query scope=Query.query(Criteria.where("userId").is(user.getId()).and("key").is(key));
      return mongo.findOne(scope,IdempotencyRecord.class).flatMap(record->{
        if(!hash.equals(record.getRequestHash()))return Mono.error(ApiException.conflict("IDEMPOTENCY_CONFLICT"));
        if(!"COMPLETED".equals(record.getStatus()))return Mono.error(ApiException.conflict("IDEMPOTENCY_IN_PROGRESS"));
        return Mono.just(record.getResponse());
      }).switchIfEmpty(Mono.defer(()->tx.execute(status->{
        IdempotencyRecord record=new IdempotencyRecord();record.setId(UUID.randomUUID().toString());record.setUserId(user.getId());record.setKey(key);
        record.setRequestHash(hash);record.setStatus("IN_PROGRESS");record.setCreatedAt(clock.instant());record.setExpiresAt(clock.instant().plus(Duration.ofHours(retentionHours)));
        return mongo.insert(record).then(orders.placeAs(request,user)).flatMap(order->{
          record.setOrderId(order.getId());record.setResponse(ApiMapper.order(order));record.setStatus("COMPLETED");record.setCompletedAt(clock.instant());
          return mongo.save(record).map(IdempotencyRecord::getResponse);
        });
      }).single())).retryWhen(TransactionRetry.conflicts());
    })).doOnError(e->{if(metrics!=null)metrics.failed(e);});
  }
}
