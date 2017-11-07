package se.krka.nackabib;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.function.Function;

// Yes, this is similar to Guava Multimap.index, but it doesn't quite do the same thing...
public class Grouper {
  public static <T, K> List<Group<T, K>> groupBy(List<T> list, Function<T, K> keyExtractor) {
    if (list.isEmpty()) {
      return ImmutableList.of();
    }

    final ImmutableList.Builder<Group<T, K>> builder = ImmutableList.builder();
    ImmutableList.Builder<T> innerBuilder = ImmutableList.builder();
    K prev = null;
    for (T obj : list) {
      final K key = keyExtractor.apply(obj);
      if (prev != null && !prev.equals(key)) {
        builder.add(new Group<>(innerBuilder.build(), prev));
        innerBuilder = ImmutableList.builder();
      }
      innerBuilder.add(obj);
      prev = key;
    }
    builder.add(new Group<>(innerBuilder.build(), prev));
    return builder.build();
  }

  public static class Group<T, K> {
    private final List<T> obj;
    private final K key;

    private Group(final List<T> obj, final K key) {
      this.obj = obj;
      this.key = key;
    }

    public List<T> getObjects() {
      return obj;
    }

    public K getKey() {
      return key;
    }
  }
}
