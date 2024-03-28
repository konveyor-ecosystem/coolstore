@RequestScoped
public class ShoppingCartOrderProcessor {

    @Inject
    private Logger log;

    @Inject
    @Channel("orders-topic")
    private Emitter<String> ordersTopicEmitter;

    @Inject
    private transient Emitter<String> context;

    @Resource(lookup = "java:/topic/orders")
    private Topic ordersTopic;

    public void process(ShoppingCart cart) {
        log.info("Sending order from processor: ");
        ordersTopicEmitter.send(Transformers.shoppingCartToJson(cart));
    }
}
