package fr.uge.net.tp12;

import java.nio.ByteBuffer;
import java.util.Objects;

public class MessageReader implements Reader<Message> {

    private enum State {
        DONE, WAITING, ERROR
    }

    private final StringReader loginReader = new StringReader();
    private final StringReader messageReader = new StringReader();
    private State state = State.WAITING;
    private boolean loginRead = false;
    private String login;
    private String message;


    @Override
    public ProcessStatus process(ByteBuffer bb) {
        ProcessStatus status = ProcessStatus.ERROR; // Warning: Initialized by default to a random value, just for compiling...
        if (!loginRead) {
            switch (loginReader.process(bb)) {
                case DONE -> {
                    loginRead = true;
                    login = loginReader.get();
                    status = ProcessStatus.REFILL;
                    break;
                }
                case REFILL -> {
                    status =  ProcessStatus.REFILL;
                    break;
                }
                case ERROR -> {
                    status = ProcessStatus.ERROR;
                    state = State.ERROR;
                    break;
                }
            }
        }
        switch (messageReader.process(bb)) {
            case DONE -> {
                message = messageReader.get();
                status = ProcessStatus.DONE;
                state = State.DONE;
                break;
            }
            case REFILL -> {
                status = ProcessStatus.REFILL;
                break;
            }
            case ERROR -> {
                status = ProcessStatus.ERROR;
                state = State.ERROR;
                break;
            }
        }
        return status;
    }

    @Override
    public Message get() {
        if (state != State.DONE) {
            throw new IllegalStateException();
        }
        return new Message(login, message);
    }

    @Override
    public void reset() {
        loginReader.reset();
        messageReader.reset();
        state = State.WAITING;
        loginRead = false;
        login = "";
        message = "";
    }
}
