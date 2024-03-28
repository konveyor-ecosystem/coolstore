import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

import static com.redhat.coolstore.utils.Transformers.toProduct;
import static com.redhat.coolstore.utils.Transformers.shoppingCartToJson;
import static com.redhat.coolstore.utils.Transformers.jsonToOrder;
