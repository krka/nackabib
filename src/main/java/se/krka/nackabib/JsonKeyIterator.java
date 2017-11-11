package se.krka.nackabib;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class JsonKeyIterator implements Iterable<String> {

  private final JSONObject jsonObject;

  private JsonKeyIterator(JSONObject jsonObject) {
    this.jsonObject = jsonObject;
  }

  public static JsonKeyIterator of(JSONObject jsonObject) {
    return new JsonKeyIterator(jsonObject);
  }

  @Override
  public Iterator<String> iterator() {
    return jsonObject.keys();
  }
}
