package tacos.business;
import java.time.*;
import java.util.UUID;
import javax.validation.constraints.*;
import lombok.Data;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.*;
import tacos.web.api.ApiException;
import tacos.api.dto.Requests;
@Service
public class AnnouncementService {
  @Data public static class Request extends Requests.Strict {
    @NotBlank @Size(max=500) @Pattern(regexp="[^\\p{Cntrl}]+") private String text;
    @NotNull private Announcement.Severity severity;
    @NotNull private Instant expiresAt;
  }
  @Data public static class PublicAnnouncement {
    private final String id,text;private final Announcement.Severity severity;private final Instant createdAt,expiresAt;
  }
  private final ReactiveMongoTemplate mongo;private final TransactionalOperator tx;private final Clock clock;private final int maximum;
  public AnnouncementService(ReactiveMongoTemplate mongo,TransactionalOperator tx,Clock clock,@Value("${tacocloud.announcements.max-active:10}") int maximum) {
    this.mongo=mongo;this.tx=tx;this.clock=clock;this.maximum=maximum;
  }
  Query active() { return Query.query(Criteria.where("active").is(true).and("expiresAt").gt(clock.instant())); }
  public Flux<PublicAnnouncement> list() {
    return mongo.find(active().with(Sort.by("createdAt","_id")),Announcement.class)
      .map(a->new PublicAnnouncement(a.getId(),a.getText(),a.getSeverity(),a.getCreatedAt(),a.getExpiresAt()));
  }
  private void admin(Authentication auth) { if(!IdentityService.role(auth,"ADMIN"))throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,"ADMIN_REQUIRED"); }
  public Mono<Announcement> create(Request request,Authentication auth) {
    return Mono.defer(()->{
      admin(auth);
      if(request.getText()==null || request.getText().trim().isEmpty() || request.getText().length()>500 || request.getText().chars().anyMatch(Character::isISOControl)
          || request.getSeverity()==null || request.getExpiresAt()==null || !request.getExpiresAt().isAfter(clock.instant()) || request.getExpiresAt().isAfter(clock.instant().plus(Duration.ofDays(30))))
        return Mono.error(ApiException.invalid("INVALID_ANNOUNCEMENT"));
      return tx.execute(status->mongo.upsert(Query.query(Criteria.where("_id").is("announcement-cap")),new Update().inc("revision",1),"operationLocks")
        .then(cleanExpired()).then(mongo.count(active(),Announcement.class)).flatMap(count->{
          if(count>=maximum)return Mono.error(ApiException.conflict("ANNOUNCEMENT_LIMIT"));
          Announcement a=new Announcement();a.setId(UUID.randomUUID().toString());a.setText(request.getText().trim());a.setSeverity(request.getSeverity());
          a.setCreatedAt(clock.instant());a.setExpiresAt(request.getExpiresAt());a.setCreatedBy(auth.getName());a.setActive(true);return mongo.insert(a);
        })).single();
    }).retryWhen(TransactionRetry.conflicts());
  }
  public Mono<Void> delete(String id,Authentication auth) {
    return Mono.defer(()->{admin(auth);return mongo.remove(Query.query(Criteria.where("_id").is(id)),Announcement.class)
      .flatMap(result->result.getDeletedCount()==0?Mono.error(ApiException.missing("ANNOUNCEMENT")):Mono.empty());});
  }
  public Mono<Void> cleanExpired() {return mongo.remove(Query.query(Criteria.where("expiresAt").lte(clock.instant())),Announcement.class).then();}
}
