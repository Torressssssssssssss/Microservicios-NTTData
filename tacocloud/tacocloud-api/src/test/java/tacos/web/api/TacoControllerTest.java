package tacos.web.api;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import java.util.*;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tacos.*;
import tacos.data.*;
import tacos.business.*;
import tacos.api.dto.*;
class TacoControllerTest {
  @Test void shouldReturnRecentTacos() {
    CatalogService catalog=mock(CatalogService.class);Responses.TacoResponse taco=new Responses.TacoResponse();taco.setId("A");
    when(catalog.search(null,null,null,null,null,0,12,"createdAt,desc")).thenReturn(Mono.just(new Responses.Page<>(Arrays.asList(taco),0,12,1)));
    TacoController controller=new TacoController(mock(TacoRepository.class),mock(DesignService.class),catalog,mock(SocialService.class));
    StepVerifier.create(controller.recentTacos()).assertNext(list->assertEquals("A",list.get(0).getId())).verifyComplete();
  }
  @Test void shouldResolveIngredientsBeforeSaving() {
    TacoRepository repo=mock(TacoRepository.class);DesignService designs=mock(DesignService.class);Requests.Design r=new Requests.Design();
    Taco taco=new Taco();taco.setId("A");taco.setName("Test taco");taco.setIngredients(Arrays.asList(new Ingredient("W","Wrap",Ingredient.Type.WRAP)));
    when(designs.resolve(r)).thenReturn(Mono.just(taco));when(repo.save(taco)).thenReturn(Mono.just(taco));
    TacoController controller=new TacoController(repo,designs,mock(CatalogService.class),mock(SocialService.class));
    StepVerifier.create(controller.postTaco(r)).assertNext(value->assertEquals("A",value.getId())).verifyComplete();verify(repo).save(taco);
  }
}
