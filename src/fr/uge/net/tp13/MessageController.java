package fr.uge.net.tp13;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Thread safe class to control message input
 */
public class MessageController {
    private final Queue<Message> messagesList = new ArrayDeque<>(8);
    private int pendingMessage = 0;

    public void add(Message msg) {
        synchronized (messagesList) {
            messagesList.add(msg);
            pendingMessage++;
        }
    }

    public Message poll() {
        synchronized (messagesList) {
            Message msg = messagesList.poll();
            if (msg == null) {
                throw new AssertionError("the selector should not have been woken up");
            }
            pendingMessage--;
            return msg;
        }
    }

    public boolean hasMessages() {
        synchronized (messagesList) {
            return pendingMessage > 0;
        }
    }
}
