package se.krka.nackabib;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONException;

public class JsonIterator implements Iterable<Object> {

  private final JSONArray jsonArray;

  private JsonIterator(JSONArray jsonArray) {
    this.jsonArray = jsonArray;
  }

  public static JsonIterator of(JSONArray jsonArray) {
    return new JsonIterator(jsonArray);
  }

  @Override
  public Iterator<Object> iterator() {
    final int length = jsonArray.length();
    final AtomicInteger i = new AtomicInteger();
    return new Iterator<Object>() {
      @Override
      public boolean hasNext() {
        return i.get() < length;
      }

      @Override
      public Object next() {
        try {
          return jsonArray.get(i.getAndIncrement());
        } catch (JSONException e) {
          throw new RuntimeException(e);
        }
      }
    };
  }
}
