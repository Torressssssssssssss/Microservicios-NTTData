package tacos.web.api;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.*;
import java.util.*;
import tacos.business.DeadLetterService;
@RestController @RequestMapping({"/api/admin/dead-letters","/api/v1/admin/dead-letters"})
public class ReplayController {
  private final DeadLetterService service;
  public ReplayController(DeadLetterService service) {this.service=service;}
  @GetMapping public Flux<Map<String,Object>> list(){return service.list();}
  @PostMapping("/{id}/replay") public Mono<Map<String,Boolean>> replay(@PathVariable String id,Authentication auth) {
    return service.replay(id,auth).map(applied->Collections.singletonMap("applied",applied));
  }
}
