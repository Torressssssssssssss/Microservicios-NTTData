package tacos.business;
import reactor.util.retry.Retry;
import java.time.Duration;
public final class TransactionRetry {
  private TransactionRetry() { }
  public static boolean transientFailure(Throwable error) {
    for(Throwable e=error;e!=null;e=e.getCause()) {
      if(e instanceof org.springframework.dao.TransientDataAccessException)return true;
      if(e instanceof com.mongodb.MongoException && ((com.mongodb.MongoException)e).hasErrorLabel("TransientTransactionError"))return true;
    }
    return false;
  }
  public static Retry conflicts() {
    return Retry.backoff(8,Duration.ofMillis(20)).maxBackoff(Duration.ofMillis(500))
      .filter(e->transientFailure(e) || e instanceof org.springframework.dao.DuplicateKeyException)
      .onRetryExhaustedThrow((spec,signal)->tacos.web.api.ApiException.conflict("CONCURRENT_UPDATE"));
  }
}
