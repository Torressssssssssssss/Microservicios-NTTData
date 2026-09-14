package tacos.web.api;

import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import tacos.TacoOrder;
import tacos.User;
import tacos.data.OrderRepository;

@Service
public class OrderService {
  private final OrderRepository repo;

  public OrderService(OrderRepository repo) { this.repo = repo; }

  private Mono<TacoOrder> authorized(String id, Authentication authentication) {
    return Mono.defer(() -> repo.findById(id))
        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found")))
        .map(order -> {
          boolean authenticated = authentication != null && authentication.isAuthenticated()
              && !(authentication instanceof AnonymousAuthenticationToken);
          boolean admin = authenticated && authentication.getAuthorities().stream()
              .anyMatch(role -> "ROLE_ADMIN".equals(role.getAuthority()));
          User owner = order.getUser();
          boolean owns = authenticated && owner != null && owner.getUsername() != null
              && owner.getUsername().equals(authentication.getName());
          if (!admin && !owns) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Order access denied");
          }
          return order;
        });
  }

  public Mono<TacoOrder> patch(String id, OrderPatchRequest patch, Authentication authentication) {
    return authorized(id, authentication).flatMap(order -> {
      if (patch.getDeliveryName() != null) order.setDeliveryName(patch.getDeliveryName());
      if (patch.getDeliveryStreet() != null) order.setDeliveryStreet(patch.getDeliveryStreet());
      if (patch.getDeliveryCity() != null) order.setDeliveryCity(patch.getDeliveryCity());
      if (patch.getDeliveryState() != null) order.setDeliveryState(patch.getDeliveryState());
      if (patch.getDeliveryZip() != null) order.setDeliveryZip(patch.getDeliveryZip());
      validateDelivery(order);
      return repo.save(order);
    });
  }

  public Mono<TacoOrder> replace(String id, OrderReplaceRequest request, Authentication authentication) {
    return Mono.defer(() -> {
      if (request.getId() != null && !id.equals(request.getId())) {
        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Inconsistent order ID"));
      }
      return authorized(id, authentication).flatMap(order -> {
        // Se conservan identidad, propietario, fecha, estado y datos de pago.
        order.setDeliveryName(request.getDeliveryName());
        order.setDeliveryStreet(request.getDeliveryStreet());
        order.setDeliveryCity(request.getDeliveryCity());
        order.setDeliveryState(request.getDeliveryState());
        order.setDeliveryZip(request.getDeliveryZip());
        order.setTacos(request.getTacos());
        validateDelivery(order);
        if (request.getTacos() == null || request.getTacos().isEmpty()) {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tacos are required");
        }
        return repo.save(order);
      });
    });
  }

  public Mono<Void> delete(String id, Authentication authentication) {
    return authorized(id, authentication).flatMap(order -> {
      if (order.getStatus() != null && order.getStatus() != TacoOrder.Status.CREATED) {
        return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT, "Order cannot be deleted in this state"));
      }
      return repo.deleteById(id);
    });
  }

  private void validateDelivery(TacoOrder order) {
    String[] values = {order.getDeliveryName(), order.getDeliveryStreet(), order.getDeliveryCity(),
        order.getDeliveryState(), order.getDeliveryZip()};
    for (String value : values) {
      if (value == null || value.trim().isEmpty()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Delivery fields must not be blank");
      }
    }
  }
}
