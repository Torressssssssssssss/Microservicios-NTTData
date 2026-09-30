package tacos.web.api;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.http.*;
import reactor.core.publisher.*;
import tacos.Ingredient;
import tacos.data.IngredientRepository;
import tacos.api.dto.*;
import tacos.api.dto.Requests.IngredientRequest;
import tacos.api.dto.Responses.IngredientResponse;
@RestController @RequestMapping(path={"/api/ingredients","/api/v1/ingredients"},produces="application/json")
public class IngredientController {
  private final IngredientRepository repo;
  public IngredientController(IngredientRepository repo) { this.repo=repo; }
  @GetMapping public Flux<IngredientResponse> allIngredients() { return repo.findAll().map(ApiMapper::ingredient); }
  @GetMapping("/{id}") public Mono<IngredientResponse> byId(@PathVariable String id) { return repo.findById(id).switchIfEmpty(Mono.error(ApiException.missing("INGREDIENT"))).map(ApiMapper::ingredient); }
  @PutMapping("/{id}") public Mono<ResponseEntity<IngredientResponse>> updateIngredient(@PathVariable String id,@Valid @RequestBody IngredientRequest request) {
    return Mono.defer(()->{
      if(!id.equals(request.getId()))return Mono.error(new ApiException(HttpStatus.BAD_REQUEST,"INCONSISTENT_ID"));
      return repo.findById(id).switchIfEmpty(Mono.error(ApiException.missing("INGREDIENT"))).flatMap(i->{
        i.setName(request.getName());i.setType(request.getType());return repo.save(i);
      }).map(ApiMapper::ingredient).map(ResponseEntity::ok);
    });
  }
  @PostMapping public Mono<ResponseEntity<IngredientResponse>> postIngredient(@Valid @RequestBody IngredientRequest request,HttpServletRequest http) {
    ServletUriComponentsBuilder location=ServletUriComponentsBuilder.fromRequestUri(http);
    return Mono.defer(()->repo.save(new Ingredient(request.getId(),request.getName(),request.getType())))
      .map(saved->ResponseEntity.created(location.cloneBuilder().pathSegment(saved.getId()).build().encode().toUri()).body(ApiMapper.ingredient(saved)));
  }
  @DeleteMapping("/{id}") public Mono<ResponseEntity<Void>> deleteIngredient(@PathVariable String id) {
    return Mono.defer(()->repo.findById(id)).switchIfEmpty(Mono.error(ApiException.missing("INGREDIENT")))
      .flatMap(i->repo.deleteById(id).thenReturn(ResponseEntity.noContent().build()));
  }
}
