package org.example;

import java.util.HashMap;
import java.util.Map;

public class CommandConfirmationManager {
    private Map<String, Long> confirmedCommands;
    private final long confirmationTimeout;

    public CommandConfirmationManager(long confirmationTimeout) {
        this.confirmedCommands = new HashMap<>();
        this.confirmationTimeout = confirmationTimeout;
    }

    public boolean isCommandConfirmed(String command) {
        Long confirmedAt = confirmedCommands.get(command);
        if (confirmedAt == null) {
            return false;
        }
        return System.currentTimeMillis() - confirmedAt < confirmationTimeout;
    }

    public void confirmCommand(String command) {
        confirmedCommands.put(command, System.currentTimeMillis());
    }

    public void removeExpiredConfirmations() {
        long now = System.currentTimeMillis();
        confirmedCommands.entrySet().removeIf(entry -> now - entry.getValue() >= confirmationTimeout);
    }
}
