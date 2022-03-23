package fr.uge.net.tp12;

import java.util.Objects;

public record Message(String login, String text) {
    public Message{
        Objects.requireNonNull(login);
        Objects.requireNonNull(text);
    }
}
