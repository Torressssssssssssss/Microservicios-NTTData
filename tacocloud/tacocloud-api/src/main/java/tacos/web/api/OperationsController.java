package tacos.web.api;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.*;
import tacos.*;
import tacos.business.*;
import tacos.api.dto.*;
@RestController
public class OperationsController {
  private final CatalogService catalog;private final KitchenService kitchen;private final WorkflowService workflow;
  public OperationsController(CatalogService catalog,KitchenService kitchen,WorkflowService workflow) { this.catalog=catalog;this.kitchen=kitchen;this.workflow=workflow; }
  @PatchMapping({"/api/admin/ingredients/{id}/catalog","/api/v1/admin/ingredients/{id}/catalog"}) public Mono<Responses.IngredientAdminResponse> catalog(@PathVariable String id,@Valid @RequestBody Requests.Catalog request) { return catalog.catalog(id,request).map(ApiMapper::ingredientAdmin); }
  @PostMapping({"/api/admin/ingredients/{id}/stock-adjustments","/api/v1/admin/ingredients/{id}/stock-adjustments"}) public Mono<Responses.IngredientAdminResponse> stock(@PathVariable String id,@Valid @RequestBody Requests.Stock request) { return catalog.adjust(id,request.getDelta()).map(ApiMapper::ingredientAdmin); }
  @GetMapping({"/api/kitchen/queue","/api/v1/kitchen/queue"}) public Flux<Responses.KitchenOrder> queue() { return kitchen.queue().map(ApiMapper::kitchen); }
  @PostMapping({"/api/kitchen/orders/claim","/api/v1/kitchen/orders/claim"}) public Mono<Responses.KitchenOrder> claim(Authentication auth) { return kitchen.claim(auth).map(ApiMapper::kitchen); }
  @PatchMapping({"/api/kitchen/orders/{id}/status","/api/v1/kitchen/orders/{id}/status"}) public Mono<Responses.KitchenOrder> status(@PathVariable String id,@Valid @RequestBody Requests.Transition request,Authentication auth) {
    return workflow.transition(id,request.getStatus(),request.getVersion(),request.getReason(),auth).map(ApiMapper::kitchen);
  }
}
