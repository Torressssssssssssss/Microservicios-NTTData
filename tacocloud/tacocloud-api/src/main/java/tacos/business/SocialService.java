package tacos.business;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.*;
import org.bson.Document;
import java.util.*;
import tacos.*;
import tacos.data.TacoRepository;
import tacos.api.dto.*;
import tacos.web.api.ApiException;
@Service
public class SocialService {
  private final ReactiveMongoTemplate mongo;private final IdentityService identities;private final TacoRepository tacos;private final CatalogService catalog;private final int minimum;
  public SocialService(ReactiveMongoTemplate mongo,IdentityService identities,TacoRepository tacos,CatalogService catalog,
      @Value("${tacocloud.rating.minimum-votes:2}") int minimum) { this.mongo=mongo;this.identities=identities;this.tacos=tacos;this.catalog=catalog;this.minimum=minimum; }
  private Mono<Taco> published(String id) { return tacos.findById(id).filter(Taco::isPublished).switchIfEmpty(Mono.error(ApiException.missing("TACO"))); }
  private Query pair(String user,String taco) { return Query.query(Criteria.where("userId").is(user).and("tacoId").is(taco)); }
  public Mono<Void> favorite(String tacoId,Authentication auth) {
    return identities.user(auth).flatMap(user->published(tacoId).flatMap(taco->mongo.upsert(pair(user.getId(),tacoId),
      new Update().setOnInsert("userId",user.getId()).setOnInsert("tacoId",tacoId),Favorite.class)))
      .onErrorResume(org.springframework.dao.DuplicateKeyException.class,e->Mono.empty()).then();
  }
  public Mono<Void> unfavorite(String tacoId,Authentication auth) { return identities.user(auth).flatMap(user->mongo.remove(pair(user.getId(),tacoId),Favorite.class)).then(); }
  public Mono<Responses.Page<Responses.TacoResponse>> favorites(int page,int size,Authentication auth) {
    catalog.pageBounds(page,size);
    return identities.user(auth).flatMap(user->{
      Query q=Query.query(Criteria.where("userId").is(user.getId()));
      return mongo.find(Query.of(q).with(Sort.by("tacoId")).skip((long)page*size).limit(size),Favorite.class)
        .concatMap(f->tacos.findById(f.getTacoId()).filter(Taco::isPublished).map(ApiMapper::taco)
          .switchIfEmpty(mongo.remove(f).then(Mono.empty())))
        .collectList().flatMap(values->mongo.count(q,Favorite.class).map(count->new Responses.Page<>(values,page,size,count)));
    });
  }
  public Mono<Void> rate(String tacoId,int score,Authentication auth) {
    if(score<1 || score>5)return Mono.error(new IllegalArgumentException("Invalid score"));
    return identities.user(auth).flatMap(user->published(tacoId).flatMap(taco->{
      Update update=new Update().set("userId",user.getId()).set("tacoId",tacoId).set("score",score);
      return mongo.upsert(pair(user.getId(),tacoId),update,TacoRating.class)
        .onErrorResume(org.springframework.dao.DuplicateKeyException.class,e->mongo.updateFirst(pair(user.getId(),tacoId),update,TacoRating.class));
    })).then();
  }
  public Flux<Document> top(int limit) {
    if(limit<1 || limit>100) return Flux.error(new IllegalArgumentException("Invalid limit"));
    List<AggregationOperation> pipeline=new ArrayList<>();
    pipeline.add(context->new Document("$group",new Document("_id","$tacoId").append("average",new Document("$avg","$score"))
      .append("count",new Document("$sum",1)).append("scores",new Document("$push","$score"))));
    pipeline.add(Aggregation.match(Criteria.where("count").gte(minimum)));
    pipeline.add(context->new Document("$lookup",new Document("from","taco").append("let",new Document("tacoId","$_id"))
      .append("pipeline",Arrays.asList(new Document("$match",new Document("$expr",new Document("$eq",Arrays.asList(new Document("$toString","$_id"),"$$tacoId"))))))
      .append("as","taco")));
    pipeline.add(Aggregation.match(Criteria.where("taco.published").is(true)));
    pipeline.add(Aggregation.sort(Sort.by(Sort.Order.desc("average"),Sort.Order.desc("count"),Sort.Order.asc("_id"))));
    pipeline.add(Aggregation.limit(limit));
    return mongo.aggregate(Aggregation.newAggregation(pipeline),TacoRating.class,Document.class).map(row->{
      List<Integer> scores=(List<Integer>)row.remove("scores");Map<String,Long> distribution=new LinkedHashMap<>();
      for(int score=1;score<=5;score++){final int value=score;distribution.put(String.valueOf(score),scores.stream().filter(x->x==value).count());}
      row.remove("taco");row.put("distribution",distribution);
      row.put("average",java.math.BigDecimal.valueOf(((Number)row.get("average")).doubleValue()).setScale(2,java.math.RoundingMode.HALF_UP));return row;
    });
  }
}
