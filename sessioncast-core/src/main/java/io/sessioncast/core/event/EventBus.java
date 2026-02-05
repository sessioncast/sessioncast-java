package io.sessioncast.core.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Simple event bus for publishing and subscribing to events.
 */
public class EventBus implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(EventBus.class);

    private final Map<Class<?>, List<Consumer<?>>> subscribers = new ConcurrentHashMap<>();
    private final ExecutorService executor;
    private final boolean asyncDispatch;

    public EventBus() {
        this(true);
    }

    public EventBus(boolean asyncDispatch) {
        this.asyncDispatch = asyncDispatch;
        this.executor = asyncDispatch ? Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "sessioncast-event");
            t.setDaemon(true);
            return t;
        }) : null;
    }

    /**
     * Subscribe to events of a specific type.
     */
    @SuppressWarnings("unchecked")
    public <T extends Event> Disposable subscribe(Class<T> eventType, Consumer<T> handler) {
        List<Consumer<?>> handlers = subscribers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>());
        handlers.add(handler);

        return Disposable.fromRunnable(() -> handlers.remove(handler));
    }

    /**
     * Subscribe to all events.
     */
    public Disposable subscribeAll(Consumer<Event> handler) {
        return subscribe(Event.class, handler);
    }

    /**
     * Publish an event to all subscribers.
     */
    @SuppressWarnings("unchecked")
    public void publish(Event event) {
        if (event == null) return;

        // Notify specific type subscribers
        notifySubscribers(event.getClass(), event);

        // Notify all-events subscribers
        if (event.getClass() != Event.class) {
            notifySubscribers(Event.class, event);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void notifySubscribers(Class<?> eventType, Event event) {
        List<Consumer<?>> handlers = subscribers.get(eventType);
        if (handlers == null || handlers.isEmpty()) return;

        for (Consumer handler : handlers) {
            if (asyncDispatch && executor != null) {
                executor.submit(() -> safeHandle(handler, event));
            } else {
                safeHandle(handler, event);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void safeHandle(Consumer handler, Event event) {
        try {
            handler.accept(event);
        } catch (Exception e) {
            log.error("Error handling event {}: {}", event.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    /**
     * Clear all subscribers.
     */
    public void clear() {
        subscribers.clear();
    }

    @Override
    public void close() {
        clear();
        if (executor != null) {
            executor.shutdown();
        }
    }
}
