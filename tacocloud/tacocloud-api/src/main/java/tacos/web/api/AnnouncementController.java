package tacos.web.api;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.*;
import tacos.business.*;
@RestController
public class AnnouncementController {
  private final AnnouncementService service;
  public AnnouncementController(AnnouncementService service) { this.service=service; }
  @GetMapping({"/api/announcements","/api/v1/announcements"})
  public Flux<AnnouncementService.PublicAnnouncement> list() {return service.list();}
  @PostMapping({"/api/admin/announcements","/api/v1/admin/announcements"}) @ResponseStatus(HttpStatus.CREATED)
  public Mono<Announcement> create(@Valid @RequestBody AnnouncementService.Request request,Authentication auth) {return service.create(request,auth);}
  @DeleteMapping({"/api/admin/announcements/{id}","/api/v1/admin/announcements/{id}"}) @ResponseStatus(HttpStatus.NO_CONTENT)
  public Mono<Void> delete(@PathVariable String id,Authentication auth) {return service.delete(id,auth);}
}
