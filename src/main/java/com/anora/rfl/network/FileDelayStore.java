package com.anora.rfl.network;

import com.anora.rfl.core.SignalValue;
import com.anora.rfl.network.util.DelayStore;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

/**
 * Super-simple disk persistence using a .properties file.
 *
 * Delay key format: dim,x,y,z  (example: "0,10,64,-3" = 8)
 *
 * Repeater runtime state keys:
 *   dim,x,y,z.rpt.lastIn
 *   dim,x,y,z.rpt.countdown
 *   dim,x,y,z.rpt.pending
 *   dim,x,y,z.rpt.out
 */
public final class FileDelayStore implements DelayStore {

    private final Path file;
    private final Properties props = new Properties();

    public FileDelayStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
        load();
    }

    private static String baseKey(NetPos pos) {
        // Assumes your NetPos has dim(), x(), y(), z() accessors (as used elsewhere in your codebase).
        return pos.dim() + "," + pos.x() + "," + pos.y() + "," + pos.z();
    }

    private static String kDelay(NetPos pos)      { return baseKey(pos); }
    private static String kLastIn(NetPos pos)     { return baseKey(pos) + ".rpt.lastIn"; }
    private static String kCountdown(NetPos pos)  { return baseKey(pos) + ".rpt.countdown"; }
    private static String kPending(NetPos pos)    { return baseKey(pos) + ".rpt.pending"; }
    private static String kOut(NetPos pos)        { return baseKey(pos) + ".rpt.out"; }

    private static int parseInt(String s, int fallback) {
        if (s == null) return fallback;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static SignalValue parseSignal(String s, SignalValue fallback) {
        if (s == null) return fallback;
        try {
            return SignalValue.valueOf(s.trim());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    @Override
    public int getDelayTicks(NetPos pos, int defaultDelayTicks) {
        return parseInt(props.getProperty(kDelay(pos)), defaultDelayTicks);
    }

    @Override
    public void setDelayTicks(NetPos pos, int delayTicks) {
        props.setProperty(kDelay(pos), Integer.toString(Math.max(0, delayTicks)));
    }

    @Override
    public RepeaterState getRepeaterState(NetPos pos) {
        String lastInS = props.getProperty(kLastIn(pos));
        String cdS     = props.getProperty(kCountdown(pos));
        String pendS   = props.getProperty(kPending(pos));
        String outS    = props.getProperty(kOut(pos));

        // If none exist, treat as "no saved state"
        if (lastInS == null && cdS == null && pendS == null && outS == null) {
            return null;
        }

        SignalValue lastIn = parseSignal(lastInS, SignalValue.OFF);
        int countdown = Math.max(0, parseInt(cdS, 0));
        SignalValue pending = parseSignal(pendS, SignalValue.OFF);
        SignalValue out = parseSignal(outS, SignalValue.OFF);

        return new RepeaterState(lastIn, countdown, pending, out);
    }

    @Override
    public void setRepeaterState(NetPos pos, RepeaterState state) {
        if (state == null) {
            clearRepeaterState(pos);
            return;
        }

        props.setProperty(kLastIn(pos), state.lastSeenInput().name());
        props.setProperty(kCountdown(pos), Integer.toString(Math.max(0, state.countdown())));
        props.setProperty(kPending(pos), state.pendingTarget().name());
        props.setProperty(kOut(pos), state.singleOut().name());
    }

    @Override
    public void clearRepeaterState(NetPos pos) {
        props.remove(kLastIn(pos));
        props.remove(kCountdown(pos));
        props.remove(kPending(pos));
        props.remove(kOut(pos));
    }

    @Override
    public void load() {
        props.clear();
        if (!Files.exists(file)) return;

        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        } catch (IOException e) {
            System.err.println("[FileDelayStore] load failed: " + e.getMessage());
        }
    }

    @Override
    public void save() {
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
        } catch (IOException ignored) {}

        try (OutputStream out = Files.newOutputStream(file)) {
            props.store(out, "RFL repeater delays + repeater runtime state (temporary, until block NBT exists)");
        } catch (IOException e) {
            System.err.println("[FileDelayStore] save failed: " + e.getMessage());
        }
    }
}
