package com.raidminer.helper.storage;

import java.nio.file.Path;

public final class PendingScreenshot {
    private final Path path;
    private final String datetime;
    private boolean consumed;

    public PendingScreenshot(Path path, String datetime) {
        this.path = path;
        this.datetime = datetime;
    }

    public Path path() { return path; }
    public String datetime() { return datetime; }
    public boolean consumed() { return consumed; }
    public void markConsumed() { this.consumed = true; }
}
