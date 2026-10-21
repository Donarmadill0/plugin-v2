package com.godvoice;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * An idle, tall black figure with antlers and long arms, built from vanilla block display
 * entities (no resource pack needed), with a light-beam of particles behind it.
 */
final class GodModel {
    private final NamespacedKey ownerKey;
    private final NamespacedKey rootKey;

    GodModel(GodVoicePlugin plugin) {
        ownerKey = new NamespacedKey(plugin, "model_owner");
        rootKey = new NamespacedKey(plugin, "model_root");
    }

    /** One box. (cx,cy,cz) = centre of the box in model space, (sx,sy,sz) = size, rotations in degrees. */
    private BlockDisplay part(Location base, String owner, Material m,
                              double cx, double cy, double cz,
                              double sx, double sy, double sz,
                              double rxDeg, double rzDeg, boolean glow) {
        Quaternionf q = new Quaternionf()
                .rotateZ((float) Math.toRadians(rzDeg))
                .rotateX((float) Math.toRadians(rxDeg));
        Vector3f half = new Vector3f((float) sx / 2f, (float) sy / 2f, (float) sz / 2f);
        Vector3f t = new Vector3f((float) cx, (float) cy, (float) cz).sub(q.transform(half, new Vector3f()));
        Transformation tr = new Transformation(t, q, new Vector3f((float) sx, (float) sy, (float) sz), new Quaternionf());
        return base.getWorld().spawn(base, BlockDisplay.class, d -> {
            d.setBlock(m.createBlockData());
            d.setTransformation(tr);
            d.setViewRange(3.0f);
            d.setInvulnerable(true);
            if (glow) d.setBrightness(new Display.Brightness(15, 15));
            d.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner);
        });
    }

    void spawn(Player god, GodData.God g) {
        Location base = god.getLocation().clone();
        Vector fwd = base.getDirection().setY(0);
        if (fwd.lengthSquared() < 1e-6) fwd = new Vector(0, 0, 1);
        base.add(fwd.normalize().multiply(4));
        base.setYaw(god.getLocation().getYaw() + 180f); // face the player
        base.setPitch(0f);
        String owner = god.getUniqueId().toString();
        Material B = Material.BLACK_CONCRETE;

        // legs
        for (int s = -1; s <= 1; s += 2) part(base, owner, B, s * 0.22, 1.2, 0, 0.3, 2.4, 0.3, 0, 0, false);
        // waist (root: particles are emitted from here)
        BlockDisplay waist = part(base, owner, B, 0, 2.8, 0, 0.5, 0.8, 0.32, 0, 0, false);
        waist.getPersistentDataContainer().set(rootKey, PersistentDataType.INTEGER, g.color & 0xFFFFFF);
        // chest, shoulders, neck, head
        part(base, owner, B, 0, 3.95, 0, 1.0, 1.5, 0.45, 0, 0, false);
        part(base, owner, B, 0, 4.6, 0, 1.7, 0.22, 0.4, 0, 0, false);
        part(base, owner, B, 0, 5.0, 0, 0.28, 0.5, 0.28, 0, 0, false);
        part(base, owner, B, 0, 5.55, 0, 0.55, 0.65, 0.5, 0, 0, false);
        // glowing eyes
        for (int s = -1; s <= 1; s += 2) part(base, owner, Material.WHITE_CONCRETE, s * 0.14, 5.6, 0.26, 0.1, 0.06, 0.04, 0, 0, true);

        for (int s = -1; s <= 1; s += 2) {
            // long arms, slightly flared, with claws
            part(base, owner, B, s * 1.06, 3.07, 0, 0.16, 3.0, 0.16, 0, s * 8, false);
            part(base, owner, Material.CRYING_OBSIDIAN, s * 1.3, 1.45, 0, 0.22, 0.5, 0.12, 0, s * 8, false);
            // antlers
            part(base, owner, B, s * 0.47, 6.33, 0, 0.1, 1.1, 0.1, 0, -s * 30, false);
            part(base, owner, B, s * 0.9, 6.5, 0, 0.08, 0.9, 0.08, 0, -s * 70, false);
            part(base, owner, B, s * 0.85, 7.19, 0, 0.08, 0.8, 0.08, 0, -s * 15, false);
            part(base, owner, B, s * 1.45, 6.85, 0, 0.07, 1.0, 0.07, 0, -s * 85, false);
        }
    }

    int remove(Player god) {
        String owner = god.getUniqueId().toString();
        int n = 0;
        for (World w : Bukkit.getWorlds()) {
            for (BlockDisplay d : w.getEntitiesByClass(BlockDisplay.class)) {
                String o = d.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
                if (owner.equals(o)) {
                    d.remove();
                    n++;
                }
            }
        }
        return n;
    }

    /** Light beam + mist around every model near a player. Call every tick. */
    void tickParticles(int tick) {
        if (tick % 4 != 0) return;
        Set<UUID> seen = new HashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (Entity e : p.getNearbyEntities(48, 40, 48)) {
                if (!(e instanceof BlockDisplay d) || !seen.add(d.getUniqueId())) continue;
                Integer color = d.getPersistentDataContainer().get(rootKey, PersistentDataType.INTEGER);
                if (color != null) emit(d.getLocation(), color);
            }
        }
    }

    private void emit(Location base, int color) {
        World w = base.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Particle.DustOptions glow = new Particle.DustOptions(Color.fromRGB(color), 2.2f);
        Particle.DustOptions mist = new Particle.DustOptions(Color.fromRGB(0x0A0A12), 1.8f);
        for (int i = 0; i < 10; i++) { // vertical light beam
            double a = r.nextDouble() * Math.PI * 2, rad = r.nextDouble() * 1.1;
            w.spawnParticle(Particle.DUST, base.getX() + Math.cos(a) * rad, base.getY() + r.nextDouble() * 16,
                    base.getZ() + Math.sin(a) * rad, 1, 0, 0, 0, 0, glow);
        }
        for (int i = 0; i < 6; i++) { // dark mist around the legs
            double a = r.nextDouble() * Math.PI * 2, rad = r.nextDouble() * 0.7;
            w.spawnParticle(Particle.DUST, base.getX() + Math.cos(a) * rad, base.getY() + r.nextDouble() * 2.6,
                    base.getZ() + Math.sin(a) * rad, 1, 0, 0, 0, 0, mist);
        }
        if (r.nextInt(3) == 0) {
            w.spawnParticle(Particle.END_ROD, base.getX() + r.nextDouble(-1, 1), base.getY() + r.nextDouble() * 8,
                    base.getZ() + r.nextDouble(-1, 1), 1, 0, 0, 0, 0);
        }
    }
}
