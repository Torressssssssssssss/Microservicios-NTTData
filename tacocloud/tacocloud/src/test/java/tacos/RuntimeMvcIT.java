package tacos;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.testcontainers.containers.*;
import java.util.*;
import java.time.Duration;
import java.math.BigDecimal;
import tacos.business.*;
import tacos.data.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.*;
@SpringBootTest(classes=TacoCloudApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
 properties={"spring.profiles.active=test","tacocloud.messaging.transport=rabbit","tacocloud.outbox.interval-ms=200","tacocloud.metrics.refresh-ms=200","spring.data.mongodb.auto-index-creation=false","spring.main.web-application-type=servlet"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RuntimeMvcIT {
  static final MongoDBContainer MONGO=new MongoDBContainer("mongo:7.0.15");
  static final RabbitMQContainer RABBIT=new RabbitMQContainer("rabbitmq:3.13.7-management");
  @DynamicPropertySource static void configuration(DynamicPropertyRegistry registry) {
    MONGO.start();RABBIT.start();registry.add("spring.data.mongodb.uri",MONGO::getReplicaSetUrl);
    registry.add("spring.rabbitmq.host",RABBIT::getHost);registry.add("spring.rabbitmq.port",RABBIT::getAmqpPort);
    registry.add("spring.rabbitmq.username",RABBIT::getAdminUsername);registry.add("spring.rabbitmq.password",RABBIT::getAdminPassword);
  }
  @LocalServerPort int port;
  @Autowired ReactiveMongoTemplate mongo;@Autowired UserRepository users;@Autowired IngredientRepository ingredients;@Autowired TacoRepository tacos;
  @Autowired PasswordEncoder encoder;@Autowired ObjectMapper json;@Autowired RabbitTemplate rabbit;
  WebTestClient http;String csrf;final String password="lab-test-password-123";
  @BeforeAll void seed() {
    http=WebTestClient.bindToServer().baseUrl("http://localhost:"+port).responseTimeout(Duration.ofSeconds(30)).build();
    Map<?,?> token=http.get().uri("/api/v1/auth/csrf").exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();csrf=(String)token.get("token");
    for(String name:Arrays.asList("alice","bob","admin","kitchen")) {
      User u=new User(name,encoder.encode(password),name,"Street","City","ST","12345","000",name+"@example.test");u.setId(name);
      u.setRoles(Collections.singleton(name.equals("admin")?"ADMIN":name.equals("kitchen")?"KITCHEN":"USER"));users.save(u).block();
    }
    for(Ingredient i:Arrays.asList(new Ingredient("W","Wrap",Ingredient.Type.WRAP),new Ingredient("V","Vegetable",Ingredient.Type.VEGGIES))) {
      i.setStockOnHand(100);i.setUnitPrice(new BigDecimal("1.25"));i.setDietaryTags(EnumSet.allOf(Ingredient.DietaryTag.class));ingredients.save(i).block();
    }
    Taco taco=new Taco();taco.setId("demo-taco");taco.setName("Browser taco");taco.setIngredients(ingredients.findAll().collectList().block());DesignService.classify(taco);tacos.save(taco).block();
  }
  WebTestClient client(String name) {
    WebTestClient.Builder builder=http.mutate().defaultCookie("XSRF-TOKEN",csrf).defaultHeader("X-XSRF-TOKEN",csrf);
    if(name!=null)builder.defaultHeaders(headers->headers.setBasicAuth(name,password));return builder.build();
  }
  String payment(String name) {
    return (String)client(name).post().uri("/api/v1/payment-methods/tokenize").bodyValue(Collections.singletonMap("syntheticCard","test_4242"))
      .exchange().expectStatus().isCreated().expectBody(Map.class).returnResult().getResponseBody().get("id");
  }
  Map<String,Object> request(String pay,int quantity) {
    Map<String,Object> r=new LinkedHashMap<>();r.put("deliveryName","Student");r.put("deliveryStreet","Lab street");r.put("deliveryCity","City");r.put("deliveryState","ST");r.put("deliveryZip","12345");r.put("paymentMethodId",pay);
    r.put("items",Arrays.asList(Map.of("taco",Map.of("name","HTTP taco","ingredientIds",Arrays.asList("W","V")),"quantity",quantity)));return r;
  }
  JsonNode place(String name,Map<String,Object> body,String key) {
    return client(name).post().uri("/api/v1/orders").header("Idempotency-Key",key).header("X-Correlation-Id","http-e2e-correlation")
      .bodyValue(body).exchange().expectStatus().isCreated().expectHeader().valueEquals("X-Correlation-Id","http-e2e-correlation").expectBody(JsonNode.class).returnResult().getResponseBody();
  }
  @Test void mvcHttpSecurityRegistrationAndAliasContracts() {
    client(null).get().uri("/api/v1/ingredients").exchange().expectStatus().isOk();
    client(null).post().uri("/api/v1/orders").bodyValue(Map.of()).exchange().expectStatus().isUnauthorized();
    client("alice").post().uri("/api/v1/ingredients").bodyValue(Map.of("id","DENIED","name","Denied","type","WRAP")).exchange().expectStatus().isForbidden();
    client("alice").get().uri("/api/v1/kitchen/queue").exchange().expectStatus().isForbidden();
    client("kitchen").get().uri("/api/v1/admin/orders").exchange().expectStatus().isForbidden();
    client("admin").get().uri("/new-unlisted-route").exchange().expectStatus().isForbidden();
    client("admin").get().uri("/data-api/orders").exchange().expectStatus().isForbidden();
    client("alice").get().uri("/actuator/metrics").exchange().expectStatus().isForbidden();
    client("admin").get().uri("/actuator/metrics").exchange().expectStatus().isOk();
    http.post().uri("/api/v1/auth/login").bodyValue(Map.of("username","alice","password",password)).exchange().expectStatus().isForbidden();
    client(null).post().uri("/api/v1/auth/register").bodyValue(Map.of("username","registered","password",password,"email","registered@example.test"))
      .exchange().expectStatus().isCreated().expectBody().jsonPath("$.password").doesNotExist();
    client("registered").get().uri("/api/v1/auth/me").exchange().expectStatus().isOk();
    client(null).post().uri("/api/v1/auth/register").bodyValue(Map.of("username","registered","password",password,"email","different@example.test")).exchange().expectStatus().isEqualTo(409);
    assertTrue(encoder.matches(password,users.findByUsername("registered").block().getPassword()));
    client(null).get().uri("/api/ingredients").exchange().expectStatus().isOk().expectHeader().valueEquals("Deprecation","true");
    client(null).get().uri("/api/v1/ingredients").exchange().expectStatus().isOk().expectHeader().doesNotExist("Deprecation");
    client(null).get().uri("/openapi.yaml").exchange().expectStatus().isOk();
  }
  @Test void fullOrderOutboxBrokerRedeliveryAndSafeContract() throws Exception {
    Map<String,Object> request=request(payment("alice"),2);JsonNode order=place("alice",request,"runtime-checkout-key");
    assertEquals(0,new BigDecimal("5.00").compareTo(order.get("total").decimalValue()));assertSchema("OrderResponse",order);
    assertEquals(order,place("alice",request,"runtime-checkout-key"));
    Map<String,Object> changed=new LinkedHashMap<>(request);changed.put("deliveryZip","99999");
    JsonNode conflict=client("alice").post().uri("/api/v1/orders").header("Idempotency-Key","runtime-checkout-key").bodyValue(changed)
      .exchange().expectStatus().isEqualTo(409).expectBody(JsonNode.class).returnResult().getResponseBody();assertSchema("ApiProblem",conflict);
    String id=order.get("id").asText();
    client("bob").get().uri("/api/v1/users/me/orders/"+id).exchange().expectStatus().isForbidden();
    client("alice").patch().uri("/api/v1/orders/"+id).bodyValue(Map.of("deliveryZip","54321")).exchange().expectStatus().isOk().expectBody().jsonPath("$.deliveryState").isEqualTo("ST");
    client("alice").patch().uri("/api/v1/orders/"+id).bodyValue(Map.of("status","DELIVERED")).exchange().expectStatus().isBadRequest();
    client("alice").put().uri("/api/v1/orders/"+id).bodyValue(new LinkedHashMap<String,Object>(request){{put("id","different");}}).exchange().expectStatus().isBadRequest();
    await().atMost(Duration.ofSeconds(20)).untilAsserted(()->assertNotNull(mongo.findById(id,KitchenTicket.class).block()));
    OutboxEvent event=mongo.findOne(Query.query(Criteria.where("payload.payload.orderId").is(id)),OutboxEvent.class).block();
    assertEquals("http-e2e-correlation",event.getPayload().getCorrelationId());
    assertFalse(json.writeValueAsString(event.getPayload()).matches(".*(paymentToken|ccNumber|ccCVV|password|deliveryStreet).*"));
    rabbit.convertAndSend("tacocloud.orders",json.writeValueAsString(event.getPayload()));
    await().atMost(Duration.ofSeconds(10)).untilAsserted(()->assertEquals(1,mongo.count(Query.query(Criteria.where("_id").is(event.getId())),ProcessedEvent.class).block()));
    client("alice").post().uri("/api/v1/orders/"+id+"/cancel").bodyValue(Map.of()).exchange().expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("CANCELLED");
    client("alice").post().uri("/api/v1/orders/"+id+"/cancel").bodyValue(Map.of()).exchange().expectStatus().isOk();
  }
  @Test void rabbitPermanentMessageGoesToSanitizedDlqWithCorrelation() {
    org.springframework.amqp.core.MessageProperties props=new org.springframework.amqp.core.MessageProperties();props.setCorrelationId("dlq-correlation");
    rabbit.send("tacocloud.orders",new org.springframework.amqp.core.Message("invalid synthetic payload".getBytes(java.nio.charset.StandardCharsets.UTF_8),props));
    await().atMost(Duration.ofSeconds(20)).untilAsserted(()->assertEquals(1,mongo.count(Query.query(Criteria.where("correlationId").is("dlq-correlation")),DeadLetterRecord.class).block()));
    org.springframework.amqp.core.Message dlq=rabbit.receive("tacocloud.orders.dlq",10000);assertNotNull(dlq);
    assertEquals("dlq-correlation",dlq.getMessageProperties().getCorrelationId());assertEquals("INVALID_EVENT",dlq.getMessageProperties().getHeaders().get("x-failure-cause"));
    assertFalse(new String(dlq.getBody(),java.nio.charset.StandardCharsets.UTF_8).contains("invalid synthetic payload"));
    client("alice").get().uri("/api/v1/admin/dead-letters").exchange().expectStatus().isForbidden();
    client("admin").get().uri("/api/v1/admin/dead-letters").exchange().expectStatus().isOk();
  }
  @Test void announcementsUseValidatedDurableIdsAndPrivateAuthors() {
    Map<String,Object> body=Map.of("text","Kitchen open","severity","INFO","expiresAt",java.time.Instant.now().plusSeconds(600).toString());
    client("alice").post().uri("/api/v1/admin/announcements").bodyValue(body).exchange().expectStatus().isForbidden();
    String id=client("admin").post().uri("/api/v1/admin/announcements").bodyValue(body).exchange().expectStatus().isCreated().expectBody(JsonNode.class).returnResult().getResponseBody().get("id").asText();
    client(null).get().uri("/api/v1/announcements").exchange().expectStatus().isOk().expectBody().jsonPath("$[0].id").isEqualTo(id).jsonPath("$[0].createdBy").doesNotExist();
    client("admin").delete().uri("/api/v1/admin/announcements/"+id).exchange().expectStatus().isNoContent();
  }

  @Test void confirmedOrderSurvivesRealBrokerOutageAndResumes() throws Exception {
    assertEquals(0,RABBIT.execInContainer("rabbitmqctl","stop_app").getExitCode());
    String id;
    try {
      JsonNode order=place("alice",request(payment("alice"),1),"broker-outage-key");id=order.get("id").asText();
      await().atMost(Duration.ofSeconds(10)).untilAsserted(()->{
        OutboxEvent row=mongo.findOne(Query.query(Criteria.where("payload.payload.orderId").is(id)),OutboxEvent.class).block();
        assertNotNull(row);assertNotEquals("PUBLISHED",row.getState());assertTrue(row.getAttempts()>0);
      });
    } finally {assertEquals(0,RABBIT.execInContainer("rabbitmqctl","start_app").getExitCode());}
    await().atMost(Duration.ofSeconds(45)).untilAsserted(()->{
      OutboxEvent row=mongo.findOne(Query.query(Criteria.where("payload.payload.orderId").is(id)),OutboxEvent.class).block();assertEquals("PUBLISHED",row.getState());
      assertNotNull(mongo.findById(id,KitchenTicket.class).block());
    });
  }
  @Test void kitchenHttpQueueClaimAndWorkflowUseSafeDtos() {
    JsonNode order=place("alice",request(payment("alice"),1),"kitchen-http-order-key");
    client("kitchen").get().uri("/api/v1/kitchen/queue").exchange().expectStatus().isOk().expectBody().jsonPath("$[0].deliveryStreet").doesNotExist();
    JsonNode claimed=client("kitchen").post().uri("/api/v1/kitchen/orders/claim").bodyValue(Map.of()).exchange().expectStatus().isOk().expectBody(JsonNode.class).returnResult().getResponseBody();
    assertEquals("ACCEPTED",claimed.get("status").asText());assertFalse(claimed.has("paymentLast4"));assertFalse(claimed.has("deliveryName"));
    String id=claimed.get("id").asText();
    for(String status:Arrays.asList("PREPARING","READY","OUT_FOR_DELIVERY","DELIVERED")) {
      claimed=client("kitchen").patch().uri("/api/v1/kitchen/orders/"+id+"/status").bodyValue(Map.of("status",status,"version",claimed.get("version").asLong(),"reason","runtime test"))
        .exchange().expectStatus().isOk().expectBody(JsonNode.class).returnResult().getResponseBody();assertEquals(status,claimed.get("status").asText());
    }
  }
  void assertSchema(String name,JsonNode payload)throws Exception {
    String yaml=new String(getClass().getResourceAsStream("/static/openapi.yaml").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
    JsonNode spec=json.valueToTree(new org.yaml.snakeyaml.Yaml().load(yaml));
    JsonNode definitions=json.readTree(spec.path("components").path("schemas").toString().replace("#/components/schemas/","#/definitions/"));
    nullable(definitions);ObjectNode schema=json.createObjectNode();schema.put("$ref","#/definitions/"+name);schema.set("definitions",definitions);
    Set<ValidationMessage> errors=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V4).getSchema(schema).validate(payload);assertTrue(errors.isEmpty(),errors.toString());
  }
  void nullable(JsonNode node) {
    if(node.isObject()){if(node.path("nullable").asBoolean() && node.has("type")){String type=node.get("type").asText();((ObjectNode)node).putArray("type").add(type).add("null");}node.elements().forEachRemaining(this::nullable);}
    else if(node.isArray())node.elements().forEachRemaining(this::nullable);
  }
  @Test void browserCanSignInAddDesignedTacoAndSubmitQuantityTwo() {
    try(com.microsoft.playwright.Playwright playwright=com.microsoft.playwright.Playwright.create();com.microsoft.playwright.Browser browser=playwright.chromium().launch(
        new com.microsoft.playwright.BrowserType.LaunchOptions().setHeadless(true).setExecutablePath(java.nio.file.Paths.get(System.getenv().getOrDefault("TACO_BROWSER_EXECUTABLE","/usr/bin/google-chrome"))).setArgs(Arrays.asList("--no-sandbox")))) {
      com.microsoft.playwright.Page page=browser.newPage();page.navigate("http://localhost:"+port+"/ui/login");
      page.locator("#loginUsername").fill("bob");page.locator("#loginPassword").fill(password);
      page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,new com.microsoft.playwright.Page.GetByRoleOptions().setName("Sign in").setExact(true)).click();
      page.waitForURL("**/ui/home");page.getByText("Latest designs",new com.microsoft.playwright.Page.GetByTextOptions().setExact(true)).click();
      page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,new com.microsoft.playwright.Page.GetByRoleOptions().setName("Favorito").setExact(true)).first().click();
      com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat(page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,new com.microsoft.playwright.Page.GetByRoleOptions().setName("Quitar favorito")).first()).isVisible();
      page.reload();com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat(page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,new com.microsoft.playwright.Page.GetByRoleOptions().setName("Quitar favorito")).first()).isVisible();
      page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,new com.microsoft.playwright.Page.GetByRoleOptions().setName("Agregar al carrito")).first().click();
      page.locator("a:has(img[src='assets/cart.png'])").click();
      page.locator("tbody select").selectOption("2");
      for(String field:Arrays.asList("deliveryName","deliveryStreet","deliveryCity"))page.locator("input[name='"+field+"']").fill("Browser lab");
      page.locator("select[name='deliveryState']").selectOption("CA");page.locator("input[name='deliveryZip']").fill("12345");
      page.getByText("Crear metodo de pago",new com.microsoft.playwright.Page.GetByTextOptions().setExact(true)).click();
      com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat(page.locator("input[name='paymentMethodId']")).not().hasValue("");
      com.microsoft.playwright.Response response=page.waitForResponse(r->r.url().endsWith("/api/v1/orders") && r.request().method().equals("POST"),()->page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,new com.microsoft.playwright.Page.GetByRoleOptions().setName("Submit Order")).click());
      assertEquals(201,response.status(),response.text());
      try {JsonNode order=json.readTree(response.text());assertEquals(2,order.path("items").get(0).path("quantity").asInt());
        assertEquals(2,mongo.findById(order.get("id").asText(),TacoOrder.class).block().getItems().get(0).getQuantity());
      }catch(java.io.IOException e){throw new RuntimeException(e);}
      com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat(page.getByText(java.util.regex.Pattern.compile("Orden creada:"))).isVisible();
    }
  }
}
