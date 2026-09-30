package tacos.kitchen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages="tacos")
@org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories("tacos.data")
public class TacoKitchenApplication {

  public static void main(String[] args) {
    SpringApplication.run(TacoKitchenApplication.class, args);
  }

}
