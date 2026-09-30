package tacos.web.api;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.*;
import java.util.*;
import tacos.*;
import tacos.data.TacoRepository;
import tacos.business.*;
import tacos.api.dto.*;
@RestController @RequestMapping({"/api/tacos","/api/v1/tacos"})
public class TacoController {
  private final TacoRepository tacos;private final DesignService designs;private final CatalogService catalog;private final SocialService social;
  public TacoController(TacoRepository tacos,DesignService designs,CatalogService catalog,SocialService social) { this.tacos=tacos;this.designs=designs;this.catalog=catalog;this.social=social; }
  @GetMapping public Mono<Responses.Page<Responses.TacoResponse>> search(
      @RequestParam(required=false) String name,@RequestParam(required=false) String ingredientId,
      @RequestParam(required=false) Ingredient.DietaryTag diet,@RequestParam(required=false) Ingredient.Allergen excludeAllergen,
      @RequestParam(required=false) Integer spice,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
      @RequestParam(defaultValue="createdAt,desc") String sort) { return catalog.search(name,ingredientId,diet,excludeAllergen,spice,page,size,sort); }
  @GetMapping(params="recent") public Mono<List<Responses.TacoResponse>> recentTacos() { return catalog.search(null,null,null,null,null,0,12,"createdAt,desc").map(Responses.Page::getContent); }
  @PostMapping @ResponseStatus(HttpStatus.CREATED) public Mono<Responses.TacoResponse> postTaco(@Valid @RequestBody Requests.Design request) { return designs.resolve(request).flatMap(tacos::save).map(ApiMapper::taco); }
  @PostMapping("/validate") public Mono<Map<String,Object>> validate(@Valid @RequestBody Requests.Design request) {
    return designs.resolve(request).map(taco->{Map<String,Object> r=new LinkedHashMap<>();r.put("valid",true);r.put("taco",ApiMapper.taco(taco));return r;});
  }
  @GetMapping("/{id}") public Mono<Responses.TacoResponse> tacoById(@PathVariable String id) { return tacos.findById(id).filter(Taco::isPublished).switchIfEmpty(Mono.error(ApiException.missing("TACO"))).flatMap(catalog::current).map(ApiMapper::taco); }
  @GetMapping("/{id}/classification") public Mono<Map<String,Object>> classification(@PathVariable String id) {
    return tacoById(id).map(taco->{Map<String,Object> r=new LinkedHashMap<>();r.put("dietaryTags",taco.getDietaryTags());r.put("allergens",taco.getAllergens());r.put("spiceLevel",taco.getSpiceLevel());
      r.put("notice","Metadata academica; no sustituye controles de contaminacion cruzada");return r;});
  }
  @GetMapping("/today") public Mono<Map<String,Object>> today() { return catalog.today(); }
  @GetMapping("/top") public Flux<org.bson.Document> top(@RequestParam(defaultValue="10") int limit) { return social.top(limit); }
  @PutMapping("/{id}/rating") @ResponseStatus(HttpStatus.NO_CONTENT) public Mono<Void> rate(@PathVariable String id,@Valid @RequestBody Requests.Rating request,org.springframework.security.core.Authentication auth) { return social.rate(id,request.getScore(),auth); }
}
