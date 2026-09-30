package tacos.web.api;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.http.MediaType;
import org.springframework.web.filter.ForwardedHeaderFilter;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.util.concurrent.atomic.AtomicBoolean;
import tacos.*;
import tacos.data.IngredientRepository;
import tacos.api.dto.Requests.IngredientRequest;
class FunctionalChallengesTest {
  IngredientRepository repo; IngredientController controller; MockMvc mvc;
  @BeforeEach void setup() {
    repo=mock(IngredientRepository.class);controller=new IngredientController(repo);
    mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiProblemHandler()).addFilters(new ForwardedHeaderFilter()).build();
    when(repo.findById(any(String.class))).thenReturn(Mono.empty());
    when(repo.save(any())).thenAnswer(c->Mono.just(c.getArgument(0)));when(repo.deleteById(any(String.class))).thenReturn(Mono.empty());
  }
  MvcResult call(MockHttpServletRequestBuilder request,int status)throws Exception {
    MvcResult r=mvc.perform(request).andReturn();if(r.getRequest().isAsyncStarted())r=mvc.perform(asyncDispatch(r)).andReturn();
    assertEquals(status,r.getResponse().getStatus(),r.getResponse().getContentAsString());return r;
  }
  IngredientRequest request(String id) { IngredientRequest r=new IngredientRequest();r.setId(id);r.setName("Updated");r.setType(Ingredient.Type.WRAP);return r; }
  @Test void ingredientPutIsLazyAndCompletes() {
    Ingredient old=new Ingredient("A","Old",Ingredient.Type.WRAP);when(repo.findById("A")).thenReturn(Mono.just(old));
    AtomicBoolean effect=new AtomicBoolean();doAnswer(c->Mono.fromSupplier(()->{effect.set(true);return c.getArgument(0);})).when(repo).save(any());
    Mono<?> work=controller.updateIngredient("A",request("A"));verify(repo,never()).save(any());assertFalse(effect.get());
    StepVerifier.create(work).expectNextCount(1).verifyComplete();assertTrue(effect.get());assertEquals("Updated",old.getName());verify(repo).save(old);
  }
  @Test void ingredientPutHttpContracts() throws Exception {
    when(repo.findById("A")).thenReturn(Mono.just(new Ingredient("A","Old",Ingredient.Type.WRAP)));
    call(put("/api/ingredients/A").contentType(MediaType.APPLICATION_JSON).content("{\"id\":\"A\",\"name\":\"Updated\",\"type\":\"WRAP\"}"),200);
    call(put("/api/ingredients/A").contentType(MediaType.APPLICATION_JSON).content("{\"id\":\"B\",\"name\":\"Updated\",\"type\":\"WRAP\"}"),400);
    call(put("/api/ingredients/missing").contentType(MediaType.APPLICATION_JSON).content("{\"id\":\"missing\",\"name\":\"Updated\",\"type\":\"WRAP\"}"),404);verify(repo,times(1)).save(any());
  }
  @Test void ingredientDeleteWaitsForEffectAndSecondCallIs404()throws Exception {
    AtomicBoolean present=new AtomicBoolean(true);
    when(repo.findById("A")).thenAnswer(c->present.get()?Mono.just(new Ingredient("A","Test",Ingredient.Type.WRAP)):Mono.empty());
    when(repo.deleteById("A")).thenReturn(Mono.fromRunnable(()->present.set(false)));
    assertEquals("",call(delete("/api/ingredients/A"),204).getResponse().getContentAsString());assertFalse(present.get());
    call(delete("/api/ingredients/A"),404);call(get("/api/ingredients/A"),404);verify(repo,times(1)).deleteById("A");
  }
  @Test void locationUsesRequestAndPersistedId()throws Exception {
    Ingredient saved=new Ingredient("generated","Test",Ingredient.Type.WRAP);doReturn(Mono.just(saved)).when(repo).save(any());when(repo.findById("generated")).thenReturn(Mono.just(saved));
    MvcResult r=call(post("https://example.test:9443/course/api/ingredients").contextPath("/course").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Test\",\"type\":\"WRAP\"}"),201);
    assertEquals("https://example.test:9443/course/api/ingredients/generated",r.getResponse().getHeader("Location"));call(get(r.getResponse().getHeader("Location")).contextPath("/course"),200);
  }
  @Test void locationSupportsForwardedHeaders()throws Exception {
    MvcResult r=call(post("/api/ingredients").header("X-Forwarded-Host","public.example.test").header("X-Forwarded-Proto","https").header("X-Forwarded-Prefix","/course")
      .contentType(MediaType.APPLICATION_JSON).content("{\"id\":\"A\",\"name\":\"Test\",\"type\":\"WRAP\"}"),201);
    assertEquals("https://public.example.test/course/api/ingredients/A",r.getResponse().getHeader("Location"));
  }
  @Test void errorsAreSafeAndMassAssignmentIsRejected()throws Exception {
    String body=call(post("/api/ingredients").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"),400).getResponse().getContentAsString();
    assertTrue(body.contains("violations"));assertTrue(body.contains("/api/ingredients"));assertFalse(body.contains("stackTrace"));
    call(post("/api/ingredients").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Test\",\"type\":\"WRAP\",\"stockOnHand\":999}"),400);
    verify(repo,never()).save(any());
  }
}
