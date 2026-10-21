package com.godvoice;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shows god dialogue as a text box floating at the side of the target's crosshair.
 * The text display entity is hidden from everyone except the target.
 */
public final class DialogueManager {
    private record Line(String title, int color, String text) {}

    private final GodVoicePlugin plugin;
    private final Map<UUID, Deque<Line>> queues = new HashMap<>();
    private final Map<UUID, Active> active = new HashMap<>();

    DialogueManager(GodVoicePlugin plugin) {
        this.plugin = plugin;
    }

    public void send(Player target, String title, int color, String text) {
        int max = plugin.getConfig().getInt("box.max-length", 300);
        if (text.length() > max) text = text.substring(0, max);
        UUID id = target.getUniqueId();
        queues.computeIfAbsent(id, k -> new ArrayDeque<>()).add(new Line(title, color, text));
        if (!active.containsKey(id)) next(id);
    }

    public void clear(UUID id) {
        queues.remove(id);
        Active a = active.remove(id);
        if (a != null) a.stop();
    }

    public void clearAll() {
        for (UUID id : new ArrayList<>(active.keySet())) clear(id);
    }

    private void next(UUID id) {
        Deque<Line> q = queues.get(id);
        Player p = Bukkit.getPlayer(id);
        if (q == null || q.isEmpty() || p == null) {
            queues.remove(id);
            return;
        }
        Line line = q.poll();
        Active a = new Active(id, line, p);
        active.put(id, a);
    }

    private static final double PX = 0.03125; // blocks per font pixel at scale 1 (approx.)

    private Location anchor(Player p, double right, double down) {
        double dist = plugin.getConfig().getDouble("box.distance", 2.3);
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Vector r = dir.clone().crossProduct(new Vector(0, 1, 0));
        if (r.lengthSquared() < 1e-6) r = new Vector(1, 0, 0);
        r.normalize();
        Vector up = r.clone().crossProduct(dir).normalize();
        return eye.clone()
                .add(dir.clone().multiply(dist))
                .add(r.multiply(right))
                .add(up.multiply(-down));
    }

    /** Width of a character in Minecraft's default font, in pixels (incl. 1px spacing). */
    private static int charPx(char c) {
        switch (c) {
            case ' ': return 4;
            case '!': case '.': case ',': case ':': case ';': case 'i': case '|': return 2;
            case '\'': case '`': case 'l': return 3;
            case 'I': case '[': case ']': case 't': return 4;
            case '"': case '(': case ')': case '*': case '<': case '>': case 'f': case 'k': case '{': case '}': return 5;
            case '@': case '~': return 7;
            default: return 6;
        }
    }

    private static int px(String s, boolean bold) {
        int w = 0;
        for (char c : s.toCharArray()) w += charPx(c) + (bold ? 1 : 0);
        return w;
    }

    private static List<String> wrap(String text, int widthPx) {
        List<String> rows = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String w : text.trim().split("\\s+")) {
            if (cur.length() > 0 && px(cur + " " + w, false) > widthPx) {
                rows.add(cur.toString());
                cur.setLength(0);
            }
            if (cur.length() > 0) cur.append(' ');
            cur.append(w);
        }
        if (cur.length() > 0) rows.add(cur.toString());
        return rows;
    }

    /** Spaces needed to pad a row of width w up to width T (plus a 1-space gap before the bar). */
    private static int pad(int T, int w) {
        return Math.max(1, (T - w + 2) / 4 + 1);
    }

    private final class Active extends BukkitRunnable {
        private final UUID id;
        private final int hold;
        private final TextDisplay display;
        private int age;

        private final double right;
        private final double down;

        Active(UUID id, Line line, Player p) {
            this.id = id;
            this.hold = Math.min(400, 60 + line.text().length() * 3);

            FileConfiguration c = plugin.getConfig();
            final float scale = (float) c.getDouble("box.scale", 0.38);
            int alpha = Math.max(0, Math.min(255, c.getInt("box.background-alpha", 215)));
            List<String> rows = wrap(line.text(), c.getInt("box.wrap-px", 130));

            int T = px(line.title(), true);
            for (String r : rows) T = Math.max(T, px(r, false));
            T = Math.max(T, 90);

            TextColor barColor = TextColor.color(line.color() & 0xFFFFFF);
            Component bar = Component.text(" \u2590", barColor); // coloured bar on the RIGHT

            // title row, underlined across the full width of the box
            Component built = Component.empty()
                    .append(Component.text(line.title(), NamedTextColor.WHITE, TextDecoration.BOLD, TextDecoration.UNDERLINED))
                    .append(Component.text(" ".repeat(pad(T, px(line.title(), true)) ), NamedTextColor.WHITE, TextDecoration.UNDERLINED))
                    .append(bar);
            for (String row : rows) {
                built = built.append(Component.newline())
                        .append(Component.text(row, NamedTextColor.WHITE))
                        .append(Component.text(" ".repeat(pad(T, px(row, false))), NamedTextColor.WHITE))
                        .append(bar);
            }
            final Component comp = built;

            double halfW = (T + 12) * PX * scale / 2.0;
            double halfH = ((rows.size() + 1) * 9 + 2) * PX * scale / 2.0;
            this.right = c.getDouble("box.left-edge", 0.85) + halfW;
            this.down = c.getDouble("box.top", 0.1) + halfH;

            this.display = p.getWorld().spawn(anchor(p, right, down), TextDisplay.class, d -> {
                d.setVisibleByDefault(false);
                d.setPersistent(false);
                d.setBillboard(Display.Billboard.CENTER);
                d.setBackgroundColor(Color.fromARGB(alpha, 0, 0, 0));
                d.setShadowed(true);
                d.setSeeThrough(false);
                d.setAlignment(TextDisplay.TextAlignment.LEFT);
                d.setLineWidth(1000);
                d.setTeleportDuration(2);
                d.setTransformation(new Transformation(
                        new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
                d.text(comp);
            });
            p.showEntity(plugin, display);
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 0.6f);
            runTaskTimer(plugin, 1L, 1L);
        }

        @Override
        public void run() {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !display.isValid() || !p.getWorld().equals(display.getWorld()) || ++age > hold) {
                finish();
                return;
            }
            display.teleport(anchor(p, right, down));
        }

        void stop() {
            try {
                cancel();
            } catch (IllegalStateException ignored) {
            }
            display.remove();
        }

        private void finish() {
            stop();
            active.remove(id);
            next(id);
        }
    }
}
