package fr.uge.net.tp12;

import java.nio.ByteBuffer;

public class MessageReader implements Reader<Message> {

    private final StringReader stringReader = new StringReader();
    private State state = State.WAITING_LOGIN;
    private String login;
    private String text;

    @Override
    public ProcessStatus process(ByteBuffer bb) {
        if (state == State.WAITING_LOGIN) {
            switch (stringReader.process(bb)) {
                case REFILL -> {
                    return ProcessStatus.REFILL;
                }
                case ERROR -> {
                    state = State.ERROR;
                    return ProcessStatus.ERROR;
                }
                case DONE -> {
                    login = stringReader.get();
                    state = State.WAITING_TEXT;
                }
            }
        }

        stringReader.reset();
        switch (stringReader.process(bb)) {
            case REFILL -> {
                return ProcessStatus.REFILL;
            }
            case ERROR -> {
                state = State.ERROR;
                return ProcessStatus.ERROR;
            }
            case DONE -> {
                text = stringReader.get();
                state = State.DONE;
            }
        }
        return ProcessStatus.DONE;
    }

    @Override
    public Message get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return new Message(login, text);
    }

    @Override
    public void reset() {
        stringReader.reset();
        state = State.WAITING_LOGIN;
        login = null;
        text = null;
    }

    private enum State {
        DONE, WAITING_LOGIN, WAITING_TEXT, ERROR
    }
}
