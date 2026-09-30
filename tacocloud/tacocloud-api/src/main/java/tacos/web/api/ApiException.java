package tacos.web.api;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
public class ApiException extends ResponseStatusException {
  private final String code;
  public ApiException(HttpStatus status, String code) { super(status, code); this.code=code; }
  public String getCode() { return code; }
  public static ApiException missing(String resource) { return new ApiException(HttpStatus.NOT_FOUND,resource+"_NOT_FOUND"); }
  public static ApiException conflict(String code) { return new ApiException(HttpStatus.CONFLICT,code); }
  public static ApiException invalid(String code) { return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,code); }
}
