package com.randomdialogue.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class PromptManager {
    private final Map<UUID, Consumer<String>> pendingCallbacks = new HashMap<>();
}
