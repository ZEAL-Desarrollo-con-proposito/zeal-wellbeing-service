package co.zeal.wellbeing.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;

/** Lanza N tareas que arrancan exactamente a la vez (misma barrera) y devuelve sus resultados. */
public final class Concurrently {

    private Concurrently() {
    }

    public static <T> List<Outcome<T>> run(int tasks, IntFunction<T> task) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(tasks)) {
            List<Future<Outcome<T>>> futures = new ArrayList<>();
            for (int i = 0; i < tasks; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        return Outcome.success(task.apply(index));
                    } catch (RuntimeException e) {
                        return Outcome.<T>failure(e);
                    }
                }));
            }
            start.countDown();
            List<Outcome<T>> outcomes = new ArrayList<>();
            for (Future<Outcome<T>> future : futures) {
                outcomes.add(future.get());
            }
            return outcomes;
        }
    }

    public record Outcome<T>(T value, RuntimeException error) {

        static <T> Outcome<T> success(T value) {
            return new Outcome<>(value, null);
        }

        static <T> Outcome<T> failure(RuntimeException error) {
            return new Outcome<>(null, error);
        }

        public boolean succeeded() {
            return error == null;
        }
    }
}
