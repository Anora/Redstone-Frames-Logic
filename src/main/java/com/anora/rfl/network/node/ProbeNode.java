package com.anora.rfl.network.node;

import com.anora.rfl.core.BundledSignal;
import com.anora.rfl.core.SignalValue;
import com.anora.rfl.network.NetPos;
import com.anora.rfl.network.util.DelayStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Debug sink node (like a lamp / logic probe).
 * Logs input changes to console AND to a file.
 */
public final class ProbeNode extends PositionedNode {

    private static final Path LOG_PATH = Path.of("logs/rfl_probe.log");

    private SignalValue lastSeen = SignalValue.OFF;

    public ProbeNode(NetPos pos, DelayStore store) {
        super(pos, store);
        this.singleOut = SignalValue.OFF;
        this.bundledOut = BundledSignal.ALL_OFF;

        ensureLogFile();
    }

    private static void ensureLogFile() {
        try {
            Files.createDirectories(LOG_PATH.getParent());
            if (!Files.exists(LOG_PATH)) {
                Files.createFile(LOG_PATH);
            }
        } catch (IOException e) {
            System.err.println("[ProbeNode] Failed to create log file: " + e.getMessage());
        }
    }

    private static void logLine(String line) {
        try {
            Files.writeString(
                    LOG_PATH,
                    line + System.lineSeparator(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            System.err.println("[ProbeNode] Failed to write log: " + e.getMessage());
        }
    }

    @Override
    public void beginPass() {
        // no output
    }

    @Override
    public boolean evaluate() {
        if (singleIn != lastSeen) {
            lastSeen = singleIn;

            String msg = "[Probe@" + pos + "] input -> " + singleIn;

            // Console
            System.out.println(msg);

            // File
            logLine(msg);

            return true;
        }
        return false;
    }
}

