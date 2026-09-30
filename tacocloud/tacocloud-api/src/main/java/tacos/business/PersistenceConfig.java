package tacos.business;
import org.springframework.context.annotation.*;
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.ReactiveMongoTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.index.*;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Flux;
import tacos.*;
@Configuration
public class PersistenceConfig {
  @Bean public ReactiveMongoTransactionManager transactionManager(ReactiveMongoDatabaseFactory factory) { return new ReactiveMongoTransactionManager(factory); }
  @Bean public TransactionalOperator transactions(ReactiveMongoTransactionManager manager) { return TransactionalOperator.create(manager); }
  @Bean public ApplicationRunner requiredIndexes(ReactiveMongoTemplate mongo) {
    // El arranque espera los indices; no se aceptan peticiones antes de garantizar unicidad.
    return args->Flux.concat(
      mongo.indexOps(IdempotencyRecord.class).ensureIndex(new Index().on("userId",Sort.Direction.ASC).on("key",Sort.Direction.ASC).unique()),
      mongo.indexOps(IdempotencyRecord.class).ensureIndex(new Index().on("expiresAt",Sort.Direction.ASC).expire(java.time.Duration.ZERO)),
      mongo.indexOps(User.class).ensureIndex(new Index().on("username",Sort.Direction.ASC).unique()),
      mongo.indexOps(User.class).ensureIndex(new Index().on("email",Sort.Direction.ASC).unique()),
      mongo.indexOps(Favorite.class).ensureIndex(new Index().on("userId",Sort.Direction.ASC).on("tacoId",Sort.Direction.ASC).unique()),
      mongo.indexOps(TacoRating.class).ensureIndex(new Index().on("userId",Sort.Direction.ASC).on("tacoId",Sort.Direction.ASC).unique()),
      mongo.indexOps(InventoryReservation.class).ensureIndex(new Index().on("orderId",Sort.Direction.ASC).unique()),
      mongo.indexOps(ReorderRecord.class).ensureIndex(new Index().on("userId",Sort.Direction.ASC).on("key",Sort.Direction.ASC).unique()),
      mongo.indexOps(TacoOrder.class).ensureIndex(new Index().on("user.id",Sort.Direction.ASC).on("placedAt",Sort.Direction.DESC).on("_id",Sort.Direction.ASC)),
      mongo.indexOps(TacoOrder.class).ensureIndex(new Index().on("status",Sort.Direction.ASC).on("placedAt",Sort.Direction.ASC).on("_id",Sort.Direction.ASC)),
      mongo.indexOps(Taco.class).ensureIndex(new Index().on("createdAt",Sort.Direction.DESC).on("_id",Sort.Direction.ASC)),
      mongo.indexOps(Taco.class).ensureIndex(new Index().on("name",Sort.Direction.ASC)),
      mongo.indexOps(Taco.class).ensureIndex(new Index().on("ingredients.id",Sort.Direction.ASC)),
      mongo.indexOps(Taco.class).ensureIndex(new Index().on("dietaryTags",Sort.Direction.ASC)),
      mongo.indexOps(OutboxEvent.class).ensureIndex(new Index().on("state",Sort.Direction.ASC).on("nextAttempt",Sort.Direction.ASC))
    ).then().block(java.time.Duration.ofSeconds(30));
  }
}
