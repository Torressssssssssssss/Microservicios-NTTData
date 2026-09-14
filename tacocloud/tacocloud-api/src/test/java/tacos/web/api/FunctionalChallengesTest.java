package tacos.web.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.ForwardedHeaderFilter;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tacos.*;
import tacos.data.*;

class FunctionalChallengesTest {
  IngredientRepository ingredients;
  OrderRepository orders;
  IngredientController ingredientController;
  OrderService service;
  MockMvc mvc;
  TacoOrder stored;
  Authentication owner = auth("alice", "ROLE_USER");

  static Authentication auth(String name, String role) {
    return new UsernamePasswordAuthenticationToken(name, "synthetic", Arrays.asList(new SimpleGrantedAuthority(role)));
  }

  @BeforeEach
  void setup() {
    ingredients = mock(IngredientRepository.class);
    orders = mock(OrderRepository.class);
    ingredientController = new IngredientController(ingredients);
    service = new OrderService(orders);
    mvc = MockMvcBuilders.standaloneSetup(ingredientController,
        new OrderApiController(orders, mock(tacos.messaging.OrderMessagingService.class),
            mock(EmailOrderService.class), service)).addPlaceholderValue("taco.api.allowed-origin", "http://localhost:8080").addFilters(new ForwardedHeaderFilter()).build();
    stored = new TacoOrder();
    stored.setId("order-1");
    stored.setUser(new User("alice", "synthetic", "Alice", "Street", "City", "ST", "12345", "000", "a@example.test"));
    stored.setDeliveryName("Alice"); stored.setDeliveryStreet("Street"); stored.setDeliveryCity("City");
    stored.setDeliveryState("ST"); stored.setDeliveryZip("12345");
    stored.setCcNumber("synthetic-payment");
    when(orders.findById("order-1")).thenReturn(Mono.just(stored));
    when(orders.findById("missing")).thenReturn(Mono.empty());
    when(orders.save(any())).thenAnswer(call -> Mono.just(call.getArgument(0)));
    when(orders.deleteById(any(String.class))).thenReturn(Mono.empty());
    when(ingredients.findById("missing")).thenReturn(Mono.empty());
    when(ingredients.save(any())).thenAnswer(call -> Mono.just(call.getArgument(0)));
    when(ingredients.deleteById(any(String.class))).thenReturn(Mono.empty());
  }

  MvcResult exchange(MockHttpServletRequestBuilder request, int status) throws Exception {
    MvcResult result = mvc.perform(request).andReturn();
    if (result.getRequest().isAsyncStarted()) result = mvc.perform(asyncDispatch(result)).andReturn();
    assertEquals(status, result.getResponse().getStatus());
    return result;
  }

  @Test
  void ingredientPutIsLazyAndCompletes() {
    Ingredient value = new Ingredient("A", "Updated", Ingredient.Type.WRAP);
    when(ingredients.findById("A")).thenReturn(Mono.just(value));
    AtomicBoolean effect = new AtomicBoolean();
    when(ingredients.save(value)).thenReturn(Mono.defer(() -> { effect.set(true); return Mono.just(value); }));
    Mono<?> operation = ingredientController.updateIngredient("A", value);
    assertFalse(effect.get()); verify(ingredients, never()).save(any());
    StepVerifier.create(operation).expectNextCount(1).verifyComplete();
    assertTrue(effect.get()); verify(ingredients, times(1)).save(value);
  }

  @Test
  void ingredientPutHttpContracts() throws Exception {
    Ingredient value = new Ingredient("A", "Updated", Ingredient.Type.WRAP);
    when(ingredients.findById("A")).thenReturn(Mono.just(value));
    exchange(put("/api/ingredients/A").contentType(MediaType.APPLICATION_JSON)
        .content("{\"id\":\"A\",\"name\":\"Updated\",\"type\":\"WRAP\"}"), 200);
    exchange(put("/api/ingredients/A").contentType(MediaType.APPLICATION_JSON)
        .content("{\"id\":\"B\",\"name\":\"Updated\",\"type\":\"WRAP\"}"), 400);
    exchange(put("/api/ingredients/A").contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\":\"Updated\",\"type\":\"WRAP\"}"), 400);
    exchange(put("/api/ingredients/missing").contentType(MediaType.APPLICATION_JSON)
        .content("{\"id\":\"missing\",\"name\":\"Updated\",\"type\":\"WRAP\"}"), 404);
    verify(ingredients, times(1)).save(any());
  }

  @Test
  void ingredientDeleteWaitsForEffectAndSecondCallIs404() throws Exception {
    AtomicBoolean present = new AtomicBoolean(true);
    when(ingredients.findById("A")).thenAnswer(call -> present.get()
        ? Mono.just(new Ingredient("A", "Test", Ingredient.Type.WRAP)) : Mono.empty());
    when(ingredients.deleteById("A")).thenReturn(Mono.fromRunnable(() -> present.set(false)));
    MvcResult result = exchange(delete("/api/ingredients/A"), 204);
    assertEquals("", result.getResponse().getContentAsString()); assertFalse(present.get());
    exchange(get("/api/ingredients/A"), 404);
    exchange(delete("/api/ingredients/A"), 404);
    exchange(delete("/api/ingredients/missing"), 404);
    verify(ingredients, times(1)).deleteById("A"); verify(ingredients, never()).deleteById("missing");
  }

  @Test
  void locationUsesRequestAndPersistedId() throws Exception {
    Ingredient saved = new Ingredient("generated", "Test", Ingredient.Type.WRAP);
    doReturn(Mono.just(saved)).when(ingredients).save(any());
    when(ingredients.findById("generated")).thenReturn(Mono.just(saved));
    MvcResult result = exchange(post("https://example.test:9443/course/api/ingredients")
        .contextPath("/course").contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\":\"Test\",\"type\":\"WRAP\"}"), 201);
    assertEquals("https://example.test:9443/course/api/ingredients/generated", result.getResponse().getHeader("Location"));
    exchange(get(result.getResponse().getHeader("Location")).contextPath("/course"), 200);
    exchange(post("/api/ingredients").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"), 400);
    verify(ingredients, times(1)).save(any());
  }

  @Test
  void locationSupportsForwardedHeaders() throws Exception {
    MvcResult result = exchange(post("/api/ingredients").header("X-Forwarded-Host", "public.example.test")
        .header("X-Forwarded-Proto", "https").header("X-Forwarded-Prefix", "/course")
        .contentType(MediaType.APPLICATION_JSON).content("{\"id\":\"A\",\"name\":\"Test\",\"type\":\"WRAP\"}"), 201);
    assertEquals("https://public.example.test/course/api/ingredients/A", result.getResponse().getHeader("Location"));
  }

  @Test
  void patchZipAndStateAreIndependent() throws Exception {
    exchange(patch("/api/orders/order-1").principal(owner).contentType(MediaType.APPLICATION_JSON)
        .content("{\"deliveryZip\":\"54321\"}"), 200);
    assertEquals("54321", stored.getDeliveryZip()); assertEquals("ST", stored.getDeliveryState());
    exchange(patch("/api/orders/order-1").principal(owner).contentType(MediaType.APPLICATION_JSON)
        .content("{\"deliveryState\":\"NEW\"}"), 200);
    assertEquals("54321", stored.getDeliveryZip()); assertEquals("NEW", stored.getDeliveryState());
    verify(orders, times(2)).save(stored);
  }

  @Test
  void patchRejectsProtectedFieldsAndInvalidResults() throws Exception {
    for (String field : Arrays.asList("id", "user", "placedAt", "total", "status", "ccNumber", "ccExpiration", "ccCVV", "tacos")) {
      exchange(patch("/api/orders/order-1").principal(owner).contentType(MediaType.APPLICATION_JSON)
          .content("{\"" + field + "\":null}"), 400);
    }
    exchange(patch("/api/orders/order-1").principal(owner).contentType(MediaType.APPLICATION_JSON)
        .content("{\"deliveryZip\":\" \"}"), 400);
    verify(orders, never()).save(any());
  }

  @Test
  void patchChecksOwnershipAndExistence() throws Exception {
    exchange(patch("/api/orders/order-1").principal(auth("bob", "ROLE_USER"))
        .contentType(MediaType.APPLICATION_JSON).content("{}"), 403);
    exchange(patch("/api/orders/order-1").contentType(MediaType.APPLICATION_JSON).content("{}"), 403);
    exchange(patch("/api/orders/missing").principal(owner).contentType(MediaType.APPLICATION_JSON).content("{}"), 404);
    verify(orders, never()).save(any());
  }

  String replacement(String id) {
    return "{\"id\":\""+id+"\",\"deliveryName\":\"Alice\",\"deliveryStreet\":\"New street\",\"deliveryCity\":\"City\","
        + "\"deliveryState\":\"ST\",\"deliveryZip\":\"12345\",\"tacos\":[{\"name\":\"Valid taco\"}]}";
  }

  @Test
  void putPreservesProtectedValuesAndRejectsRedirect() throws Exception {
    java.util.Date placed = stored.getPlacedAt(); User user = stored.getUser();
    exchange(put("/api/orders/order-1").principal(owner).contentType(MediaType.APPLICATION_JSON).content(replacement("other")), 400);
    exchange(put("/api/orders/missing").principal(owner).contentType(MediaType.APPLICATION_JSON).content(replacement("missing")), 404);
    exchange(put("/api/orders/order-1").principal(auth("bob", "ROLE_USER"))
        .contentType(MediaType.APPLICATION_JSON).content(replacement("order-1")), 403);
    exchange(put("/api/orders/order-1").principal(owner).contentType(MediaType.APPLICATION_JSON).content(replacement("order-1")), 200);
    assertEquals("order-1", stored.getId()); assertSame(user, stored.getUser()); assertEquals(placed, stored.getPlacedAt());
    assertEquals("synthetic-payment", stored.getCcNumber()); assertEquals(TacoOrder.Status.CREATED, stored.getStatus());
    verify(orders, times(1)).save(stored);
  }

  @Test
  void putRejectsPaymentAndInvalidDelivery() throws Exception {
    exchange(put("/api/orders/order-1").principal(owner).contentType(MediaType.APPLICATION_JSON)
        .content(replacement("order-1").replace("\"id\":", "\"ccNumber\":\"synthetic\",\"id\":")), 400);
    exchange(put("/api/orders/order-1").principal(owner).contentType(MediaType.APPLICATION_JSON).content("{}"), 400);
    verify(orders, never()).save(any());
  }

  @Test
  void deleteChecksOwnershipStateAndCompletion() throws Exception {
    exchange(delete("/api/orders/missing").principal(owner), 404);
    exchange(delete("/api/orders/order-1").principal(auth("bob", "ROLE_USER")), 403);
    exchange(delete("/api/orders/order-1"), 403);
    for (TacoOrder.Status status : Arrays.asList(TacoOrder.Status.PREPARING,
        TacoOrder.Status.READY, TacoOrder.Status.DELIVERED, TacoOrder.Status.CANCELLED)) {
      stored.setStatus(status);
      exchange(delete("/api/orders/order-1").principal(owner), 409);
      exchange(delete("/api/orders/order-1").principal(auth("admin", "ROLE_ADMIN")), 409);
    }
    verify(orders, never()).deleteById(any(String.class));
    stored.setStatus(TacoOrder.Status.CREATED);
    AtomicBoolean deleted = new AtomicBoolean();
    when(orders.deleteById("order-1")).thenReturn(Mono.fromRunnable(() -> deleted.set(true)));
    assertEquals("", exchange(delete("/api/orders/order-1").principal(owner), 204).getResponse().getContentAsString());
    assertTrue(deleted.get()); verify(orders, times(1)).deleteById("order-1");
  }

  @Test
  void adminCanPatchAndDeleteLegacyOrder() {
    OrderPatchRequest patch = new OrderPatchRequest(); patch.setDeliveryZip("99999");
    StepVerifier.create(service.patch("order-1", patch, auth("admin", "ROLE_ADMIN"))).expectNext(stored).verifyComplete();
    stored.setStatus(null);
    StepVerifier.create(service.delete("order-1", auth("admin", "ROLE_ADMIN"))).verifyComplete();
  }
}
