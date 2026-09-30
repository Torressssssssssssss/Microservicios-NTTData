package tacos.messaging;
import java.util.concurrent.CompletionStage;
public interface OrderMessagingService { CompletionStage<Void> sendOrder(OrderEvent event); }
