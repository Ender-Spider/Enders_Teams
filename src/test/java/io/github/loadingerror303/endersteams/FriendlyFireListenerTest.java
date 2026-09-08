package io.github.loadingerror303.endersteams;

import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FriendlyFireListenerTest {
    @TempDir Path directory;
    private TeamStore teams;
    private FriendlyFireListener listener;
    private Player owner;
    private Player member;
    private UUID teamId;

    @BeforeEach void setup() throws IOException {
        teams = new TeamStore(directory.resolve("teams.yml"));
        owner = player();
        member = player();
        teamId = teams.create(owner.getUniqueId(), "Miners", TeamIcon.DIAMOND).id();
        teams.join(teamId, member.getUniqueId());
        listener = new FriendlyFireListener(teams);
    }

    @Test void cancelsTeammateMeleeDamage() {
        EntityDamageEvent event = damage(owner, owner, member);
        listener.onDamage(event);
        verify(event).setCancelled(true);
    }

    @Test void resolvesProjectileShooterWhenDirectDamageHasNoCausingEntity() {
        Projectile arrow = mock(Projectile.class);
        when(arrow.getShooter()).thenReturn(owner);
        EntityDamageEvent event = damage(null, arrow, member);
        listener.onDamage(event);
        verify(event).setCancelled(true);
    }

    @Test void cancelsTeammateProjectileImpactAndIgnition() {
        Projectile arrow = mock(Projectile.class);
        when(arrow.getShooter()).thenReturn(owner);
        ProjectileHitEvent hit = mock(ProjectileHitEvent.class);
        when(hit.getEntity()).thenReturn(arrow);
        when(hit.getHitEntity()).thenReturn(member);
        listener.onProjectile(hit);
        verify(hit).setCancelled(true);
        EntityCombustByEntityEvent fire = mock(EntityCombustByEntityEvent.class);
        when(fire.getCombuster()).thenReturn(arrow);
        when(fire.getEntity()).thenReturn(member);
        listener.onCombust(fire);
        verify(fire).setCancelled(true);
    }

    @Test void resolvesPlayerLitTnt() {
        TNTPrimed tnt = mock(TNTPrimed.class);
        when(tnt.getSource()).thenReturn(owner);
        EntityDamageEvent event = damage(tnt, tnt, member);
        listener.onDamage(event);
        verify(event).setCancelled(true);
    }

    @Test void allowsOutsidersSelfDamageMobsAndEnvironment() {
        Entity outsider = player();
        Entity mob = mock(Entity.class);
        for (EntityDamageEvent event : new EntityDamageEvent[]{
                damage(outsider, outsider, member), damage(owner, owner, owner),
                damage(mob, mob, member), damage(null, null, member), damage(owner, owner, mob)}) {
            listener.onDamage(event);
            verify(event, never()).setCancelled(anyBoolean());
        }
    }

    @Test void enablingFriendlyFireOrKickingMemberAllowsDamageImmediately() throws IOException {
        teams.setFriendlyFire(owner.getUniqueId(), teamId, true);
        EntityDamageEvent enabled = damage(owner, owner, member);
        listener.onDamage(enabled);
        verify(enabled, never()).setCancelled(anyBoolean());
        teams.setFriendlyFire(owner.getUniqueId(), teamId, false);
        teams.kick(owner.getUniqueId(), teamId, member.getUniqueId());
        EntityDamageEvent kicked = damage(owner, owner, member);
        listener.onDamage(kicked);
        verify(kicked, never()).setCancelled(anyBoolean());
    }

    private EntityDamageEvent damage(Entity causing, Entity direct, Entity victim) {
        DamageSource source = mock(DamageSource.class);
        when(source.getCausingEntity()).thenReturn(causing);
        when(source.getDirectEntity()).thenReturn(direct);
        EntityDamageEvent event = mock(EntityDamageEvent.class);
        when(event.getDamageSource()).thenReturn(source);
        when(event.getEntity()).thenReturn(victim);
        return event;
    }

    private Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }
}
