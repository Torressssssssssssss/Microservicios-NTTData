package tacos.business;
import org.springframework.stereotype.Service;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import reactor.core.publisher.*;
import java.util.*;
import tacos.*;
import tacos.web.api.ApiException;
@Service
public class InventoryService {
  private final ReactiveMongoTemplate mongo;
  public InventoryService(ReactiveMongoTemplate mongo) { this.mongo=mongo; }
  public Map<String,Long> requirements(TacoOrder order) {
    Map<String,Long> quantities=new TreeMap<>();
    order.getItems().forEach(line->line.getTaco().getIngredients().forEach(i->quantities.merge(i.getId(),(long)line.getQuantity(),Long::sum)));
    return quantities;
  }
  // Invocar dentro de la transaccion de colocacion: un fallo revierte todas las reservas.
  public Mono<Void> reserve(TacoOrder order) {
    Map<String,Long> requested=requirements(order);
    return mongo.findById(order.getId(),InventoryReservation.class).flatMap(existing->{
      if(existing.isReleased() || !existing.getQuantities().equals(requested)) return Mono.error(ApiException.conflict("RESERVATION_CONFLICT"));
      return Mono.just(existing);
    }).switchIfEmpty(Mono.defer(()->Flux.fromIterable(requested.entrySet()).concatMap(entry->
      mongo.findAndModify(Query.query(Criteria.where("_id").is(entry.getKey()).and("available").is(true)
          .and("stockOnHand").gte(entry.getValue())),new Update().inc("stockOnHand",-entry.getValue()).inc("version",1),
          FindAndModifyOptions.options().returnNew(true),Ingredient.class)
        .switchIfEmpty(Mono.error(ApiException.conflict("INSUFFICIENT_STOCK_"+entry.getKey()))))
      .then(Mono.defer(()->{
        InventoryReservation r=new InventoryReservation();r.setId(order.getId());r.setOrderId(order.getId());r.setQuantities(requested);
        return mongo.insert(r);
      })))).then();
  }
  // La marca y la devolucion de existencias pertenecen a la misma transaccion.
  public Mono<Void> release(String orderId) {
    return mongo.findAndModify(Query.query(Criteria.where("_id").is(orderId).and("released").is(false)),
        new Update().set("released",true),InventoryReservation.class).flatMap(r->
      Flux.fromIterable(new TreeMap<>(r.getQuantities()).entrySet()).concatMap(entry->
        mongo.updateFirst(Query.query(Criteria.where("_id").is(entry.getKey())),new Update().inc("stockOnHand",entry.getValue()).inc("version",1),Ingredient.class)
          .flatMap(result->result.getMatchedCount()==1?Mono.just(result):Mono.error(ApiException.conflict("RESERVED_INGREDIENT_MISSING"))))
      .then()).then();
  }
}
