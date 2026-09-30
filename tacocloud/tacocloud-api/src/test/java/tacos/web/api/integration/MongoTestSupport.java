package tacos.web.api.integration;
import org.testcontainers.containers.MongoDBContainer;
public final class MongoTestSupport {
  private static MongoDBContainer container;
  public static MongoDBContainer container() {uri();return container;}
  public static synchronized String uri() {
    if(container==null){container=new MongoDBContainer("mongo:7.0.15");container.start();}
    return container.getReplicaSetUrl();
  }
}
