package app.hushfeed.verification.api23;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/** Deliberately unavailable API24 members. A native API23 run must reject both. */
final class Api24NegativeControl {
    static void mapDefault() {
        Map<Object, Object> map = new WeakHashMap<>();
        map.remove(new Object(), new Object());
    }
    static void concurrentSet() { ConcurrentHashMap.newKeySet(); }
    private Api24NegativeControl() { }
}
