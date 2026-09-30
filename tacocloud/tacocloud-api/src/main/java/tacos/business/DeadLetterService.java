package tacos.business;
import java.time.Clock;
import java.util.*;
import java.security.MessageDigest;
import org.springframework.stereotype.Service;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.*;
import tacos.messaging.OrderEvent;
import tacos.web.api.ApiException;
import tacos.security.Correlation;
@Service
public class DeadLetterService {
  private final ReactiveMongoTemplate mongo;private final Clock clock;private final BusinessMetrics metrics;private final IdempotentEventConsumer consumer;
  public DeadLetterService(ReactiveMongoTemplate mongo,Clock clock,BusinessMetrics metrics,IdempotentEventConsumer consumer) {
    this.mongo=mongo;this.clock=clock;this.metrics=metrics;this.consumer=consumer;
  }
  public Mono<DeadLetterRecord> store(byte[] body,OrderEvent event,String correlation,String cause) {
    return Mono.defer(()->{
      String id;
      try { byte[] digest=MessageDigest.getInstance("SHA-256").digest(body);StringBuilder hex=new StringBuilder();for(byte b:digest)hex.append(String.format("%02x",b));id=hex.toString(); }
      catch(Exception e) {return Mono.error(e);}
      DeadLetterRecord row=new DeadLetterRecord();row.setId(id);row.setCorrelationId(Correlation.validOrNew(correlation));row.setCause(cause);row.setFailedAt(clock.instant());
      try {IdempotentEventConsumer.validate(event);row.setEvent(event);} catch(Exception invalid) {row.setEvent(null);}
      return mongo.insert(row).doOnSuccess(saved->metrics.deadLetter()).onErrorResume(org.springframework.dao.DuplicateKeyException.class,e->mongo.findById(row.getId(),DeadLetterRecord.class));
    });
  }
  public Mono<Boolean> replay(String id,Authentication auth) {
    if(!IdentityService.role(auth,"ADMIN"))return Mono.error(new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,"ADMIN_REQUIRED"));
    return mongo.findById(id,DeadLetterRecord.class).switchIfEmpty(Mono.error(ApiException.missing("DEAD_LETTER"))).flatMap(row->{
      if(row.getEvent()==null)return Mono.error(ApiException.invalid("DEAD_LETTER_NOT_REPLAYABLE"));
      return consumer.consume(row.getEvent()).retryWhen(TransactionRetry.conflicts()).flatMap(applied->mongo.updateFirst(Query.query(Criteria.where("_id").is(id)),
        new Update().set("replayed",true).set("replayedAt",clock.instant()),DeadLetterRecord.class).thenReturn(applied));
    });
  }
  public Flux<Map<String,Object>> list() {
    return mongo.find(new Query().limit(100).with(org.springframework.data.domain.Sort.by("failedAt","_id")),DeadLetterRecord.class).map(row->{
      Map<String,Object> view=new LinkedHashMap<>();view.put("id",row.getId());view.put("cause",row.getCause());view.put("correlationId",row.getCorrelationId());
      view.put("failedAt",row.getFailedAt());view.put("replayed",row.isReplayed());view.put("replayable",row.getEvent()!=null);return view;
    });
  }
}
