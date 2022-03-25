package fr.uge.net.tp13;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Thread safe class to control string input
 */
public class StringController {
    private final Queue<String> stringQueue = new ArrayDeque<>(8);
    private int pendingMessage = 0;

    public void add(String msg) {
        synchronized (stringQueue) {
            stringQueue.add(msg);
            pendingMessage++;
        }
    }

    public String poll() {
        synchronized (stringQueue) {
            String msg = stringQueue.poll();
            if (msg == null) {
                throw new AssertionError("the selector should not have been woken up");
            }
            pendingMessage--;
            return msg;
        }
    }

    public boolean hasMessages() {
        synchronized (stringQueue) {
            return pendingMessage > 0;
        }
    }
}
