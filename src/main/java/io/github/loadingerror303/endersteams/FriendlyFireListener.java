package io.github.loadingerror303.endersteams;

import java.util.Collection;
import java.util.UUID;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class FriendlyFireListener implements Listener {
    private final TeamStore teams;

    public FriendlyFireListener(TeamStore teams) { this.teams = teams; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        UUID attacker = responsible(event.getDamageSource().getCausingEntity());
        if (attacker == null) attacker = responsible(event.getDamageSource().getDirectEntity());
        if (protectedFrom(attacker, event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCombust(EntityCombustByEntityEvent event) {
        if (protectedFrom(responsible(event.getCombuster()), event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectile(ProjectileHitEvent event) {
        // Splash potions need per-target filtering so beneficial potions still work.
        if (!(event.getEntity() instanceof ThrownPotion)
                && protectedFrom(responsible(event.getEntity()), event.getHitEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent event) {
        if (!harmful(event.getPotion().getEffects())) return;
        UUID attacker = responsible(event.getPotion());
        for (var target : event.getAffectedEntities()) {
            if (protectedFrom(attacker, target)) event.setIntensity(target, 0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCloud(AreaEffectCloudApplyEvent event) {
        var cloud = event.getEntity();
        boolean harmful = harmful(cloud.getCustomEffects())
                || (cloud.getBasePotionType() != null && harmful(cloud.getBasePotionType().getPotionEffects()));
        if (harmful && cloud.getSource() instanceof Player player) {
            event.getAffectedEntities().removeIf(target -> protectedFrom(player.getUniqueId(), target));
        }
    }

    private static boolean harmful(Collection<PotionEffect> effects) {
        return effects.stream().anyMatch(effect -> effect.getType().getEffectCategory() == PotionEffectType.Category.HARMFUL);
    }

    private boolean protectedFrom(UUID attacker, Entity target) {
        return attacker != null && target instanceof Player player
                && teams.blocksFriendlyFire(attacker, player.getUniqueId());
    }

    private static UUID responsible(Entity entity) {
        if (entity instanceof Player player) return player.getUniqueId();
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player.getUniqueId();
        }
        if (entity instanceof TNTPrimed tnt && tnt.getSource() instanceof Player player) return player.getUniqueId();
        if (entity instanceof Tameable tameable && tameable.getOwner() != null) return tameable.getOwner().getUniqueId();
        return null;
    }
}
