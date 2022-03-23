package fr.uge.net.tp12;

import java.util.Objects;

public record Message(String login, String message) {
    public Message{
        Objects.requireNonNull(login);
        Objects.requireNonNull(message);
    }
}
