package tacos.web.api;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.*;
import tacos.business.*;
import tacos.api.dto.*;
@RestController @RequestMapping({"/api/orders","/api/v1/orders"})
public class OrderApiController {
  private final IdempotentOrders idempotency;private final OrderApplicationService orders;private final WorkflowService workflow;private final EmailOrderService email;
  public OrderApiController(OrderApplicationService orders,WorkflowService workflow,EmailOrderService email,IdempotentOrders idempotency) { this.idempotency=idempotency; this.orders=orders;this.workflow=workflow;this.email=email; }
  @PostMapping @ResponseStatus(HttpStatus.CREATED) public Mono<Responses.OrderResponse> create(@Valid @RequestBody Requests.OrderCreateRequest request,@RequestHeader("Idempotency-Key") String key,Authentication auth) { return idempotency.place(request,key,auth); }
  @PostMapping("/quote") public Mono<Responses.OrderResponse> quote(@Valid @RequestBody Requests.OrderCreateRequest request) { return orders.quote(request).map(ApiMapper::order); }
  @PostMapping("/fromEmail") @ResponseStatus(HttpStatus.CREATED) public Mono<Responses.OrderResponse> fromEmail(@Valid @RequestBody EmailOrder request) { return orders.fromEmail(email,request).map(ApiMapper::order); }
  @PutMapping("/{id}") public Mono<Responses.OrderResponse> replace(@PathVariable String id,@Valid @RequestBody Requests.Replacement request,Authentication auth) { return orders.replace(id,request,auth).map(ApiMapper::order); }
  @PatchMapping("/{id}") public Mono<Responses.OrderResponse> patch(@PathVariable String id,@RequestBody OrderPatchRequest request,Authentication auth) { return orders.patch(id,request,auth).map(ApiMapper::order); }
  @DeleteMapping("/{id}") public Mono<ResponseEntity<Void>> delete(@PathVariable String id,Authentication auth) { return workflow.delete(id,auth).thenReturn(ResponseEntity.noContent().build()); }
  @PostMapping("/{id}/reorder") @ResponseStatus(HttpStatus.CREATED) public Mono<Responses.OrderResponse> reorder(@PathVariable String id,@Valid @RequestBody Requests.Reorder request,
      @RequestHeader("Idempotency-Key") String key,Authentication auth) { return orders.reorder(id,request,key,auth).map(ApiMapper::order); }
  @PatchMapping("/{id}/status") public Mono<Responses.OrderResponse> status(@PathVariable String id,@Valid @RequestBody Requests.Transition request,Authentication auth) {
    return workflow.transition(id,request.getStatus(),request.getVersion(),request.getReason(),auth).map(ApiMapper::order);
  }
  @PostMapping("/{id}/cancel") public Mono<Responses.OrderResponse> cancel(@PathVariable String id,Authentication auth) {
    return workflow.transition(id,tacos.TacoOrder.Status.CANCELLED,null,"USER_CANCEL",auth).map(ApiMapper::order);
  }
}
