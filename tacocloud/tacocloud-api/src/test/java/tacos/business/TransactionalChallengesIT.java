package tacos.business;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.math.*;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import org.springframework.data.mongodb.ReactiveMongoTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import reactor.core.publisher.*;
import reactor.test.StepVerifier;
import tacos.*;
import tacos.security.Correlation;
import tacos.data.*;
import tacos.api.dto.*;
import tacos.api.dto.Requests.*;
import tacos.web.api.*;
import tacos.messaging.*;
import tacos.web.api.integration.MongoTestSupport;

class TransactionalChallengesIT {
  static ReactiveMongoTemplate mongo;static com.mongodb.reactivestreams.client.MongoClient client;
  static String database;static TransactionalOperator tx;
  IngredientRepository ingredients;OrderRepository orders;TacoRepository tacos;UserRepository users;PaymentMethodRepository payments;
  IdentityService identities;InventoryService inventory;OutboxService outbox;OrderApplicationService app;WorkflowService workflow;
  CatalogService catalog;SocialService social;KitchenService kitchen;PricingService pricing;DesignService designs;
  Clock clock=Clock.fixed(Instant.parse("2026-09-17T12:00:00Z"),ZoneOffset.UTC);
  Authentication alice=auth("alice","USER"),bob=auth("bob","USER"),admin=auth("admin","ADMIN"),cook=auth("cook","KITCHEN");
  static Authentication auth(String name,String role) {return new UsernamePasswordAuthenticationToken(name,"synthetic",Arrays.asList(new SimpleGrantedAuthority("ROLE_"+role)));}
  @BeforeAll static void database() {
    database="tc30_it_"+System.nanoTime();client=com.mongodb.reactivestreams.client.MongoClients.create(MongoTestSupport.uri());
    mongo=new ReactiveMongoTemplate(client,database);
    tx=TransactionalOperator.create(new ReactiveMongoTransactionManager(mongo.getMongoDatabaseFactory()));
  }
  @AfterAll static void cleanup() {if(client!=null){Mono.from(client.getDatabase(database).drop()).block();client.close();}}
  @BeforeEach void setup()throws Exception {
    for(String collection:mongo.getCollectionNames().collectList().block())mongo.dropCollection(collection).block();
    new PersistenceConfig().requiredIndexes(mongo).run(new org.springframework.boot.DefaultApplicationArguments(new String[0]));
    ReactiveMongoRepositoryFactory factory=new ReactiveMongoRepositoryFactory(mongo);
    ingredients=factory.getRepository(IngredientRepository.class);orders=factory.getRepository(OrderRepository.class);
    tacos=factory.getRepository(TacoRepository.class);users=factory.getRepository(UserRepository.class);payments=factory.getRepository(PaymentMethodRepository.class);
    identities=new IdentityService(users);inventory=new InventoryService(mongo);outbox=new OutboxService(mongo,new EventFactory(clock),clock);
    DesignRules rules=new DesignRules();designs=new DesignService(ingredients,Arrays.asList(rules.ingredientCount(),rules.oneWrap(),rules.noDuplicates(),rules.availableIngredients(),rules.hotNeedsDrink(true,4),rules.veganPromise(true)));
    pricing=new PricingService(designs,new CouponService(new BusinessConfig.Coupons(),clock),50);
    app=new OrderApplicationService(orders,payments,identities,pricing,inventory,outbox,tx,clock,mongo);
    workflow=new WorkflowService(orders,app,inventory,outbox,tx,clock,mongo);catalog=new CatalogService(mongo,ingredients,tacos,designs,clock,100);
    social=new SocialService(mongo,identities,tacos,catalog,2);kitchen=new KitchenService(mongo,outbox,tx,clock,3,2,1);
    for(String name:Arrays.asList("alice","bob","admin","cook")) {
      User u=new User(name,"{bcrypt}synthetic",name,"Street","City","ST","12345","000",name+"@example.test");u.setId(name);users.save(u).block();
      PaymentMethod p=new PaymentMethod();p.setId("pay-"+name);p.setUserId(name);p.setPaymentToken("test-token");p.setBrand("LAB");p.setLast4("4242");payments.save(p).block();
    }
    Ingredient wrap=new Ingredient("W","Wrap",Ingredient.Type.WRAP);wrap.setUnitPrice(new BigDecimal("1.15"));wrap.setStockOnHand(20);wrap.setDietaryTags(EnumSet.allOf(Ingredient.DietaryTag.class));
    Ingredient veg=new Ingredient("V","Vegetable",Ingredient.Type.VEGGIES);veg.setUnitPrice(new BigDecimal("2.35"));veg.setStockOnHand(20);veg.setDietaryTags(EnumSet.allOf(Ingredient.DietaryTag.class));
    ingredients.save(wrap).block();ingredients.save(veg).block();
    // Crear colecciones fuera de transacciones para compatibilidad con Mongo de laboratorio.
    for(Class<?> type:Arrays.asList(TacoOrder.class,InventoryReservation.class,OutboxEvent.class,ReorderRecord.class,ProcessedEvent.class,KitchenTicket.class,StationSlot.class))
      if(!mongo.collectionExists(type).block())mongo.createCollection(type).block();
  }
  Design design(String name) {Design d=new Design();d.setName(name);d.setIngredientIds(Arrays.asList("W","V"));return d;}
  OrderCreateRequest request(String user,int quantity) {
    OrderCreateRequest r=new OrderCreateRequest();r.setDeliveryName(user);r.setDeliveryStreet("Street");r.setDeliveryCity("City");r.setDeliveryState("ST");r.setDeliveryZip("12345");r.setPaymentMethodId("pay-"+user);
    Item item=new Item();item.setTaco(design("Test taco"));item.setQuantity(quantity);r.setItems(Arrays.asList(item));return r;
  }
  long stock(String id){return ingredients.findById(id).block().getStockOnHand();}
  long count(Class<?> type){return mongo.count(new Query(),type).block();}
  void setStock(String id,long value){mongo.updateFirst(Query.query(Criteria.where("_id").is(id)),new Update().set("stockOnHand",value),Ingredient.class).block();}
  @Test void placePersistsPriceSnapshotInventoryAndExactlyOneOutbox() {
    TacoOrder order=app.place(request("alice",2),alice).block();assertEquals(new BigDecimal("7.00"),order.getTotal());
    assertEquals(18,stock("W"));assertEquals(1,count(TacoOrder.class));assertEquals(1,count(OutboxEvent.class));
    tx.transactional(inventory.reserve(order)).block();assertEquals(18,stock("W"));
    OutboxEvent event=mongo.findAll(OutboxEvent.class).single().block();assertEquals("NEW",event.getState());assertEquals(order.getId(),event.getPayload().getPayload().getOrderId());
  }
  @Test void transactionRollsBackPartialStockAndOrderOnFailure() {
    setStock("W",1);setStock("V",10);
    OrderCreateRequest r=request("alice",2);
    StepVerifier.create(app.place(r,alice)).expectError().verify();
    assertEquals(1,stock("W"));assertEquals(10,stock("V"));assertEquals(0,count(TacoOrder.class));assertEquals(0,count(OutboxEvent.class));assertEquals(0,count(InventoryReservation.class));
    OutboxService broken=mock(OutboxService.class);when(broken.append(any(),any())).thenReturn(Mono.error(new IllegalStateException("forced before commit")));
    OrderApplicationService brokenApp=new OrderApplicationService(orders,payments,identities,pricing,inventory,broken,tx,clock,mongo);
    StepVerifier.create(brokenApp.place(request("alice",1),alice)).expectError().verify();assertEquals(1,stock("W"));assertEquals(0,count(TacoOrder.class));
  }
  @Test void concurrentBuyersCannotOversell() {
    setStock("W",1);setStock("V",1);
    List<Boolean> results=Flux.merge(app.place(request("alice",1),alice).map(x->true).onErrorReturn(false),app.place(request("bob",1),bob).map(x->true).onErrorReturn(false)).collectList().block();
    assertEquals(1,results.stream().filter(x->x).count());assertEquals(0,stock("W"));assertEquals(0,stock("V"));assertEquals(1,count(TacoOrder.class));
  }
  @Test void cancellationReleasesOnceAndForbiddenTransitionDoesNotWrite() {
    TacoOrder order=app.place(request("alice",1),alice).block();
    StepVerifier.create(workflow.transition(order.getId(),TacoOrder.Status.DELIVERED,order.getVersion(),"bad",cook)).expectError(ApiException.class).verify();
    StepVerifier.create(workflow.transition(order.getId(),TacoOrder.Status.ACCEPTED,order.getVersion(),"bad",alice)).expectError(ApiException.class).verify();
    workflow.transition(order.getId(),TacoOrder.Status.CANCELLED,null,"cancel",alice).block();workflow.transition(order.getId(),TacoOrder.Status.CANCELLED,null,"repeat",alice).block();
    assertEquals(20,stock("W"));assertEquals(2,count(OutboxEvent.class));assertEquals(2,orders.findById(order.getId()).block().getHistory().size());
  }
  @Test void patchZipOwnershipPutIdentityAndDeleteRemainProtected() {
    TacoOrder order=app.place(request("alice",1),alice).block();OrderPatchRequest p=new OrderPatchRequest();p.setDeliveryZip("54321");
    TacoOrder updated=app.patch(order.getId(),p,alice).block();assertEquals("54321",updated.getDeliveryZip());assertEquals("ST",updated.getDeliveryState());
    StepVerifier.create(app.patch(order.getId(),p,bob)).expectError(ApiException.class).verify();
    StepVerifier.create(workflow.delete(order.getId(),bob)).expectError(ApiException.class).verify();
    Replacement r=new Replacement();r.setId("wrong");StepVerifier.create(app.replace(order.getId(),r,alice)).expectError(ApiException.class).verify();
    workflow.delete(order.getId(),alice).block();assertFalse(orders.existsById(order.getId()).block());assertEquals(20,stock("W"));
    StepVerifier.create(workflow.delete(order.getId(),alice)).expectError(ApiException.class).verify();
  }
  @Test void optimisticLockingAndNegativeStockAreEnforced() {
    Ingredient first=ingredients.findById("W").block(),second=ingredients.findById("W").block();first.setName("First");ingredients.save(first).block();second.setName("Stale");
    StepVerifier.create(ingredients.save(second)).expectError(org.springframework.dao.OptimisticLockingFailureException.class).verify();
    StepVerifier.create(catalog.adjust("W",-21)).expectError(ApiException.class).verify();assertEquals(20,stock("W"));
  }
  @Test void queryFiltersPagingAndDailySelectionAreStable() {
    for(String name:Arrays.asList("Alpha taco","Beta taco","Gamma taco")){Taco t=designs.resolve(design(name)).block();t.setId(name);tacos.save(t).block();}
    Responses.Page<Responses.TacoResponse> page=catalog.search("taco","W",Ingredient.DietaryTag.VEGAN,Ingredient.Allergen.MILK,2,0,2,"name,asc").block();
    assertEquals(3,page.getTotalElements());assertEquals("Alpha taco",page.getContent().get(0).getName());
    assertEquals("Gamma taco",catalog.search(null,null,null,null,null,1,2,"name,asc").block().getContent().get(0).getName());
    assertThrows(IllegalArgumentException.class,()->catalog.search(null,null,null,null,null,0,101,"name,asc"));
    Map<String,Object> first=catalog.today().block(),second=catalog.today().block();assertEquals(first,second);
    setStock("W",0);StepVerifier.create(catalog.today()).expectError(ApiException.class).verify();
  }
  @Test void favoritesAndRatingsAreIdempotentAndPrivate() {
    Taco t=designs.resolve(design("Rated taco")).flatMap(tacos::save).block();
    Flux.merge(social.favorite(t.getId(),alice),social.favorite(t.getId(),alice)).then().block();assertEquals(1,count(Favorite.class));
    assertEquals(0,social.favorites(0,10,bob).block().getContent().size());assertEquals(1,social.favorites(0,10,alice).block().getContent().size());
    social.rate(t.getId(),1,alice).block();social.rate(t.getId(),5,alice).block();social.rate(t.getId(),3,bob).block();
    org.bson.Document rank=social.top(10).single().block();assertEquals(new BigDecimal("4.00"),rank.get("average"));assertEquals(2,((Number)rank.get("count")).intValue());
    social.unfavorite(t.getId(),alice).block();social.unfavorite(t.getId(),alice).block();assertEquals(0,count(Favorite.class));
    StepVerifier.create(social.favorite("missing",alice)).expectError(ApiException.class).verify();
  }
  @Test void historyIsPrivateAndReorderUsesCurrentPriceAndIdempotentKey() {
    TacoOrder original=app.place(request("alice",1),alice).block();
    StepVerifier.create(app.owned(original.getId(),bob,false)).expectError(ApiException.class).verify();
    Ingredient i=ingredients.findById("W").block();i.setUnitPrice(new BigDecimal("5.00"));ingredients.save(i).block();
    Reorder request=new Reorder();request.setPaymentMethodId("pay-alice");
    StepVerifier.create(app.reorder(original.getId(),request,"repeat-key-1",alice)).expectError(PriceChangedException.class).verify();assertEquals(1,count(TacoOrder.class));
    request.setConfirmPriceChange(true);TacoOrder fresh=app.reorder(original.getId(),request,"repeat-key-1",alice).block();
    assertNotEquals(original.getId(),fresh.getId());assertEquals(new BigDecimal("7.35"),fresh.getTotal());
    assertEquals(fresh.getId(),app.reorder(original.getId(),request,"repeat-key-1",alice).block().getId());assertEquals(2,count(TacoOrder.class));
    assertEquals(new BigDecimal("3.50"),orders.findById(original.getId()).block().getTotal());
  }
  @Test void kitchenClaimIsAtomicAndStationCannotClaimTwice() {
    app.place(request("alice",1),alice).block();app.place(request("bob",1),bob).block();
    List<TacoOrder> claimed=Flux.merge(kitchen.claim(cook).onErrorResume(e->Mono.empty()),kitchen.claim(auth("cook2","KITCHEN")).onErrorResume(e->Mono.empty())).collectList().block();
    assertEquals(2,claimed.size());assertEquals(claimed.size(),claimed.stream().map(TacoOrder::getId).distinct().count());
    TacoOrder own=claimed.get(0);Authentication who=auth(own.getCookId(),"KITCHEN");
    StepVerifier.create(kitchen.claim(who)).expectError().verify();
    assertEquals(TacoOrder.Status.ACCEPTED,own.getStatus());assertTrue(own.getEstimatedPrepMinutes()>0);
    assertTrue(kitchen.estimate(own,10)>kitchen.estimate(own,0));
    StepVerifier.create(workflow.delete(own.getId(),admin)).expectError(ApiException.class).verify();
  }
  @Test void outboxRetriesThenPublishesAndClaimsAreExclusive() {
    app.place(request("alice",1),alice).block();AtomicInteger calls=new AtomicInteger();
    OrderMessagingService transport=e->{calls.incrementAndGet();CompletableFuture<Void> f=new CompletableFuture<>();f.completeExceptionally(new IllegalStateException("offline"));return f;};
    OutboxPublisher publisher=new OutboxPublisher(mongo,transport,clock,3);assertFalse(publisher.publishOne().block());
    OutboxEvent row=mongo.findAll(OutboxEvent.class).single().block();assertEquals("NEW",row.getState());assertEquals(1,row.getAttempts());
    Clock later=Clock.offset(clock,Duration.ofSeconds(10));OrderMessagingService healthy=e->{calls.incrementAndGet();return CompletableFuture.completedFuture(null);};
    OutboxPublisher recovered=new OutboxPublisher(mongo,healthy,later,3);
    List<Boolean> results=Flux.merge(recovered.publishOne(),recovered.publishOne()).collectList().block();assertEquals(1,results.stream().filter(x->x).count());
    assertEquals(2,calls.get());assertEquals("PUBLISHED",mongo.findAll(OutboxEvent.class).single().block().getState());
  }
  @Test void expiredClaimResumesAndConsumerDeduplicatesDurably() {
    TacoOrder order=app.place(request("alice",1),alice).block();OutboxEvent row=mongo.findAll(OutboxEvent.class).single().block();
    mongo.updateFirst(Query.query(Criteria.where("_id").is(row.getId())),new Update().set("state","PUBLISHING").set("leaseUntil",clock.instant().minusSeconds(1)),OutboxEvent.class).block();
    assertTrue(new OutboxPublisher(mongo,e->CompletableFuture.completedFuture(null),clock,3).publishOne().block());
    IdempotentEventConsumer consumer=new IdempotentEventConsumer(mongo,tx,clock);
    assertTrue(consumer.consume(row.getPayload()).block());assertFalse(consumer.consume(row.getPayload()).block());
    assertEquals(1,count(ProcessedEvent.class));assertEquals(1,count(KitchenTicket.class));
    assertFalse(new IdempotentEventConsumer(mongo,tx,clock).consume(row.getPayload()).block());
  }

  @Test void httpIdempotencyIsScopedCanonicalConcurrentAndAtomic() {
    IdempotentOrders api=new IdempotentOrders(mongo,tx,identities,app,clock,168);
    OrderCreateRequest request=request("alice",1);
    List<Responses.OrderResponse> concurrent=Flux.merge(api.place(request,"same-request-key",alice),api.place(request,"same-request-key",alice)).collectList().block();
    assertEquals(2,concurrent.size());assertEquals(concurrent.get(0).getId(),concurrent.get(1).getId());
    assertEquals(1,count(TacoOrder.class));assertEquals(1,count(OutboxEvent.class));assertEquals(1,count(InventoryReservation.class));assertEquals(19,stock("W"));
    assertEquals(concurrent.get(0),api.place(request,"same-request-key",alice).block());
    StepVerifier.create(api.place(request("alice",2),"same-request-key",alice)).expectErrorMatches(e->e instanceof ApiException && ((ApiException)e).getCode().equals("IDEMPOTENCY_CONFLICT")).verify();
    api.place(request("bob",1),"same-request-key",bob).block();assertEquals(2,count(TacoOrder.class));
    IdempotencyRecord row=mongo.findOne(Query.query(Criteria.where("userId").is("alice")),IdempotencyRecord.class).block();
    assertEquals("COMPLETED",row.getStatus());assertEquals(clock.instant().plus(Duration.ofHours(168)),row.getExpiresAt());assertTrue(row.getRequestHash().matches("[a-f0-9]{64}"));
  }
  @Test void failedIdempotentPlacementRollsBackItsKeyAndCanBeRetried() {
    IdempotentOrders api=new IdempotentOrders(mongo,tx,identities,app,clock,168);setStock("W",0);
    StepVerifier.create(api.place(request("alice",1),"retryable-key",alice)).expectError().verify();
    assertEquals(0,count(IdempotencyRecord.class));assertEquals(0,count(OutboxEvent.class));
    setStock("W",20);api.place(request("alice",1),"retryable-key",alice).block();assertEquals(1,count(TacoOrder.class));
  }
  @Test void correlationCrossesReactiveThreadAndRemainsInOutbox() {
    app.place(request("alice",1),alice).subscribeOn(reactor.core.scheduler.Schedulers.parallel())
      .contextWrite(reactor.util.context.Context.of(Correlation.KEY,"exposition-request-1")).block();
    OutboxEvent row=mongo.findAll(OutboxEvent.class).single().block();
    assertEquals("exposition-request-1",row.getPayload().getCorrelationId());assertNotEquals(row.getPayload().getPayload().getOrderId(),row.getPayload().getCorrelationId());
    assertNull(org.slf4j.MDC.get(Correlation.KEY));
  }
  @Test void announcementsPersistExpireAndEnforceConcurrentActiveLimit() {
    AnnouncementService service=new AnnouncementService(mongo,tx,clock,1);
    AnnouncementService.Request request=new AnnouncementService.Request();request.setText("Lab operational");request.setSeverity(Announcement.Severity.INFO);request.setExpiresAt(clock.instant().plusSeconds(60));
    List<Boolean> results=Flux.merge(service.create(request,admin).map(x->true).onErrorReturn(false),service.create(request,admin).map(x->true).onErrorReturn(false)).collectList().block();
    assertEquals(1,results.stream().filter(x->x).count());
    AnnouncementService restart=new AnnouncementService(mongo,tx,clock,1);assertEquals(1,restart.list().count().block());
    assertEquals(0,new AnnouncementService(mongo,tx,Clock.offset(clock,Duration.ofSeconds(61)),1).list().count().block());
    String id=restart.list().single().block().getId();StepVerifier.create(service.delete(id,alice)).expectError(ApiException.class).verify();
    service.delete(id,admin).block();assertEquals(0,service.list().count().block());
    request.setText("bad\ntext");StepVerifier.create(service.create(request,admin)).expectError(ApiException.class).verify();
  }
  @Test void businessMetricsCountCommitsNotReplaysAndGaugeAndHealthReflectStorage() {
    io.micrometer.core.instrument.simple.SimpleMeterRegistry registry=new io.micrometer.core.instrument.simple.SimpleMeterRegistry();BusinessMetrics metrics=new BusinessMetrics(registry);
    org.springframework.test.util.ReflectionTestUtils.setField(app,"metrics",metrics);org.springframework.test.util.ReflectionTestUtils.setField(workflow,"metrics",metrics);
    IdempotentOrders api=new IdempotentOrders(mongo,tx,identities,app,clock,168);
    Responses.OrderResponse order=api.place(request("alice",1),"metric-unique-key",alice).block();api.place(request("alice",1),"metric-unique-key",alice).block();
    assertEquals(1,registry.get("tacocloud.orders.created").counter().count());assertEquals(1,registry.get("tacocloud.orders.placement").timer().count());
    workflow.transition(order.getId(),TacoOrder.Status.CANCELLED,null,"test",alice).block();workflow.transition(order.getId(),TacoOrder.Status.CANCELLED,null,"test",alice).block();
    assertEquals(1,registry.get("tacocloud.orders.cancelled").counter().count());
    OperationalSnapshot snapshot=new OperationalSnapshot(mongo,new AnnouncementService(mongo,tx,clock,10),registry);snapshot.refresh().block();
    assertEquals(2,registry.get("tacocloud.outbox.pending").gauge().value());assertEquals(0,registry.get("tacocloud.kitchen.queue").gauge().value());
    assertEquals(org.springframework.boot.actuate.health.Status.UP,new OutboxHealth(mongo).health().block().getStatus());
    mongo.updateMulti(new Query(),new Update().set("state","FAILED"),OutboxEvent.class).block();
    assertEquals(org.springframework.boot.actuate.health.Status.DOWN,new OutboxHealth(mongo).health().block().getStatus());
    registry.getMeters().forEach(m->m.getId().getTags().forEach(tag->assertTrue(Arrays.asList("result","status","source","transport").contains(tag.getKey()))));
  }
  @Test void emailPlacementSubscribesOnceAndFailuresDoNotPublish() {
    EmailOrderService converter=new EmailOrderService(users,ingredients,payments);EmailOrder email=new EmailOrder();email.setEmail("alice@example.test");
    EmailOrder.EmailTaco taco=new EmailOrder.EmailTaco();taco.setName("Email taco");taco.setIngredients(Arrays.asList("W","V"));email.setTacos(Arrays.asList(taco,taco));
    Mono<TacoOrder> publisher=app.fromEmail(converter,email);assertEquals(0,count(TacoOrder.class));
    StepVerifier.create(publisher).assertNext(order->assertEquals(2,order.getItems().size())).verifyComplete();assertEquals(1,count(TacoOrder.class));assertEquals(1,count(OutboxEvent.class));
    email.setEmail("absent@example.test");StepVerifier.create(app.fromEmail(converter,email)).expectError(ApiException.class).verify();assertEquals(1,count(OutboxEvent.class));
  }
  @Test void concurrentRegistrationUsesUniqueIndexesAndPasswordRemainsPrivate() {
    tacos.security.RegistrationService registration=new tacos.security.RegistrationService(users,org.springframework.security.crypto.factory.PasswordEncoderFactories.createDelegatingPasswordEncoder());
    tacos.security.RegistrationForm form=new tacos.security.RegistrationForm();form.setUsername("new-user");form.setEmail("new-user@example.test");form.setPassword("synthetic-password");
    List<Boolean> results=Flux.merge(registration.register(form).map(x->true).onErrorReturn(false),registration.register(form).map(x->true).onErrorReturn(false)).collectList().block();
    assertEquals(1,results.stream().filter(x->x).count());assertEquals(1,mongo.count(Query.query(Criteria.where("username").is("new-user")),User.class).block());
    User user=users.findByUsername("new-user").block();assertTrue(org.springframework.security.crypto.factory.PasswordEncoderFactories.createDelegatingPasswordEncoder().matches(form.getPassword(),user.getPassword()));
  }
  @Test void staleStatusVersionAndFifoClaimAreEnforced() {
    TacoOrder first=app.place(request("alice",1),alice).block();TacoOrder second=app.place(request("bob",1),bob).block();
    List<TacoOrder> fifo=kitchen.queue().collectList().block();assertEquals(2,fifo.size());assertTrue(fifo.get(0).getId().compareTo(fifo.get(1).getId())<0);
    TacoOrder claimed=kitchen.claim(cook).block();assertEquals(fifo.get(0).getId(),claimed.getId());
    StepVerifier.create(workflow.transition(claimed.getId(),TacoOrder.Status.PREPARING,0L,"stale",cook)).expectError(ApiException.class).verify();
    TacoOrder preparing=workflow.transition(claimed.getId(),TacoOrder.Status.PREPARING,claimed.getVersion(),"prepare",cook).block();
    StepVerifier.create(workflow.transition(preparing.getId(),TacoOrder.Status.CANCELLED,null,"late",alice)).expectError(ApiException.class).verify();
    workflow.transition(preparing.getId(),TacoOrder.Status.READY,preparing.getVersion(),"ready",cook).block();
    assertNotEquals(claimed.getId(),kitchen.claim(cook).block().getId());
  }

  @Test void paymentMigrationRemovesLegacySensitiveFieldsFromMongo() throws Exception {
    org.bson.Document old=new org.bson.Document("_id","legacy").append("ccNumber","synthetic-pan").append("ccCVV","synthetic-cvv").append("ccExpiration","synthetic-expiration");
    mongo.insert(old,"tacoOrder").block();mongo.insert(new org.bson.Document(old),"paymentMethod").block();
    java.nio.file.Path script=java.nio.file.Paths.get("../scripts/migrate-payment.js").toAbsolutePath();
    org.testcontainers.containers.MongoDBContainer container=MongoTestSupport.container();
    container.copyFileToContainer(org.testcontainers.utility.MountableFile.forHostPath(script),"/tmp/migrate-payment.js");
    org.testcontainers.containers.Container.ExecResult result=container.execInContainer("mongosh",database,"--quiet","/tmp/migrate-payment.js");assertEquals(0,result.getExitCode(),result.getStderr());
    org.bson.Document migrated=mongo.findById("legacy",org.bson.Document.class,"tacoOrder").block();
    assertNotNull(migrated);assertFalse(migrated.containsKey("ccNumber"));assertFalse(migrated.containsKey("ccCVV"));assertFalse(migrated.containsKey("ccExpiration"));
    assertNull(mongo.findById("legacy",org.bson.Document.class,"paymentMethod").block());
  }

  @Test void replacementReusesItsOwnStockAndCannotChangeIdentityOrPayment() {
    setStock("W",1);setStock("V",1);TacoOrder old=app.place(request("alice",1),alice).block();
    Replacement replacement=new com.fasterxml.jackson.databind.ObjectMapper().convertValue(request("alice",1),Replacement.class);replacement.setId(old.getId());
    TacoOrder updated=app.replace(old.getId(),replacement,alice).block();
    assertEquals(old.getId(),updated.getId());assertEquals(old.getPlacedAt(),updated.getPlacedAt());assertEquals(old.getUser().getId(),updated.getUser().getId());
    assertEquals(old.getPaymentMethodId(),updated.getPaymentMethodId());assertEquals(0,stock("W"));assertEquals(0,stock("V"));
    replacement.setPaymentMethodId("pay-bob");StepVerifier.create(app.replace(old.getId(),replacement,alice)).expectError(ApiException.class).verify();
    replacement.setPaymentMethodId("pay-alice");replacement.getItems().get(0).setQuantity(2);
    StepVerifier.create(app.replace(old.getId(),replacement,alice)).expectError().verify();assertEquals(0,stock("W"));assertEquals(1,orders.findById(old.getId()).block().getItems().get(0).getQuantity());
  }
  @Test void dailySelectionChangesPredictablyAndRankingBreaksTiesById() {
    for(String id:Arrays.asList("C","A","B")){Taco taco=designs.resolve(design("Taco "+id)).block();taco.setId(id);tacos.save(taco).block();social.rate(id,5,alice).block();social.rate(id,3,bob).block();}
    List<org.bson.Document> ranking=social.top(3).collectList().block();assertEquals(Arrays.asList("A","B","C"),ranking.stream().map(x->x.getString("_id")).collect(java.util.stream.Collectors.toList()));
    Responses.TacoResponse today=(Responses.TacoResponse)catalog.today().block().get("taco");
    CatalogService tomorrow=new CatalogService(mongo,ingredients,tacos,designs,Clock.offset(clock,Duration.ofDays(1)),100);
    Responses.TacoResponse next=(Responses.TacoResponse)tomorrow.today().block().get("taco");assertNotEquals(today.getId(),next.getId());
    StepVerifier.create(social.rate("A",0,alice)).expectError(IllegalArgumentException.class).verify();
    StepVerifier.create(social.rate("missing",5,alice)).expectError(ApiException.class).verify();
    mongo.updateFirst(Query.query(Criteria.where("_id").is("A")),new Update().set("published",false),Taco.class).block();
    StepVerifier.create(social.rate("A",5,alice)).expectError(ApiException.class).verify();
  }
  @Test void concurrentReorderCannotDuplicateTheNewOrder() {
    TacoOrder original=app.place(request("alice",1),alice).block();Reorder request=new Reorder();request.setPaymentMethodId("pay-alice");
    List<TacoOrder> replies=Flux.merge(app.reorder(original.getId(),request,"concurrent-reorder-key",alice),app.reorder(original.getId(),request,"concurrent-reorder-key",alice)).collectList().block();
    assertEquals(2,replies.size());assertEquals(replies.get(0).getId(),replies.get(1).getId());assertEquals(2,count(TacoOrder.class));assertEquals(2,count(OutboxEvent.class));
  }

  @Test void consumerProjectionAndControlledReplayRemainIdempotentOutOfOrder() throws Exception {
    TacoOrder order=app.place(request("alice",1),alice).block();OutboxEvent created=mongo.findAll(OutboxEvent.class).single().block();
    workflow.transition(order.getId(),TacoOrder.Status.CANCELLED,null,"cancel",alice).block();
    OutboxEvent cancelled=mongo.findOne(Query.query(Criteria.where("payload.eventType").is("CANCELLED")),OutboxEvent.class).block();
    IdempotentEventConsumer consumer=new IdempotentEventConsumer(mongo,tx,clock);assertTrue(consumer.consume(cancelled.getPayload()).block());assertTrue(consumer.consume(created.getPayload()).block());
    assertEquals("CANCELLED",mongo.findById(order.getId(),KitchenTicket.class).block().getPayload().getStatus());assertEquals(2,count(ProcessedEvent.class));
    TacoOrder fresh=app.place(request("alice",1),alice).block();OutboxEvent event=mongo.findOne(Query.query(Criteria.where("payload.payload.orderId").is(fresh.getId())),OutboxEvent.class).block();
    io.micrometer.core.instrument.simple.SimpleMeterRegistry registry=new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
    DeadLetterService dlq=new DeadLetterService(mongo,clock,new BusinessMetrics(registry),consumer);
    byte[] body=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(event.getPayload());
    DeadLetterRecord row=dlq.store(body,event.getPayload(),event.getPayload().getCorrelationId(),"RETRY_EXHAUSTED").block();
    dlq.store(body,event.getPayload(),event.getPayload().getCorrelationId(),"RETRY_EXHAUSTED").block();assertEquals(1,registry.get("tacocloud.events.dlq").counter().count());
    StepVerifier.create(dlq.replay(row.getId(),alice)).expectError(ApiException.class).verify();
    assertTrue(dlq.replay(row.getId(),admin).block());assertFalse(dlq.replay(row.getId(),admin).block());
    assertTrue(mongo.findById(row.getId(),DeadLetterRecord.class).block().isReplayed());
  }
  @Test void metricsIncludeCouponStockFailureAndKitchenLatency() {
    io.micrometer.core.instrument.simple.SimpleMeterRegistry registry=new io.micrometer.core.instrument.simple.SimpleMeterRegistry();BusinessMetrics metrics=new BusinessMetrics(registry);
    org.springframework.test.util.ReflectionTestUtils.setField(app,"metrics",metrics);org.springframework.test.util.ReflectionTestUtils.setField(workflow,"metrics",metrics);
    CouponService coupons=(CouponService)org.springframework.test.util.ReflectionTestUtils.getField(pricing,"coupons");
    BusinessConfig.Coupons props=(BusinessConfig.Coupons)org.springframework.test.util.ReflectionTestUtils.getField(coupons,"properties");
    BusinessConfig.Coupon c=new BusinessConfig.Coupon();c.setType(BusinessConfig.Coupon.Type.FIXED);c.setValue(BigDecimal.ONE);c.setStartsAt(clock.instant());c.setExpiresAt(clock.instant().plusSeconds(60));props.getCodes().put("CLASS",c);
    OrderCreateRequest request=request("alice",1);request.setCouponCode("CLASS");TacoOrder order=app.place(request,alice).block();assertEquals(1,registry.get("tacocloud.coupons.applied").counter().count());
    TacoOrder accepted=kitchen.claim(cook).block();TacoOrder preparing=workflow.transition(accepted.getId(),TacoOrder.Status.PREPARING,accepted.getVersion(),"prepare",cook).block();
    workflow.transition(preparing.getId(),TacoOrder.Status.READY,preparing.getVersion(),"ready",cook).block();assertEquals(1,registry.get("tacocloud.kitchen.latency").timer().count());
    setStock("W",0);StepVerifier.create(app.place(request,alice)).expectError().verify();assertEquals(1,registry.get("tacocloud.orders.failed").counter().count());assertEquals(1,registry.get("tacocloud.stock.rejected").counter().count());
    assertEquals(1,registry.get("tacocloud.orders.created").counter().count());
  }
}
