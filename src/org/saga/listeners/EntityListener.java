package org.saga.listeners;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.saga.Saga;
import org.saga.SagaLogger;
import org.saga.chunks.BundleManager;
import org.saga.chunks.SagaChunk;
import org.saga.config.GeneralConfiguration;
import org.saga.factions.Faction;
import org.saga.listeners.events.SagaEntityDamageEvent;
import org.saga.listeners.events.SagaEntityDeathEvent;
import org.saga.listeners.events.SagaEventHandler;
import org.saga.metadata.SpawnerTag;
import org.saga.player.GuardianRune;
import org.saga.player.SagaPlayer;

public class EntityListener implements Listener{

	
	@EventHandler(priority = EventPriority.NORMAL)
	public void onEntityDamage(EntityDamageEvent event) {


		// Not a living:
		if(!(event.getEntity() instanceof LivingEntity)) return;
		LivingEntity defender= (LivingEntity) event.getEntity();
		
		// Damage ticks:
		if(event instanceof EntityDamageEvent && defender.getNoDamageTicks() > defender.getMaximumNoDamageTicks()/2F){
			event.setCancelled(true);
			return;
		}

		// Dead:
		if(defender.getHealth() <= 0) return;

		// Saga event:
		SagaEntityDamageEvent damageEvent = new SagaEntityDamageEvent(event, defender);
		SagaEventHandler.onEntityDamage(damageEvent);
		if(damageEvent.isCancelled()) return;
		
		// Forward to managers:
		SagaPlayer attackerPlayer = damageEvent.getAttackerPlayer();
		if(attackerPlayer != null){
			attackerPlayer.getAttributeManager().onAttack(damageEvent);
			attackerPlayer.getAbilityManager().onAttack(damageEvent);
		}
		
		SagaPlayer defenderPlayer = damageEvent.getDefenderPlayer();
		if(defenderPlayer != null){
			defenderPlayer.getAttributeManager().onDefend(damageEvent);
			defenderPlayer.getAbilityManager().onDefend(damageEvent);
		}
		
		// Apply:
		damageEvent.apply();
		
		
	}
	
	@EventHandler(priority = EventPriority.NORMAL)
	public void onProjectileHit(ProjectileHitEvent event) {
		
		
		if(!(event.getEntity() instanceof Projectile)) return;
		Projectile projectile = (Projectile) event.getEntity();
		
		if(!(projectile.getShooter() instanceof Player)) return;
		Player player = (Player) projectile.getShooter();
		
		// Get player:
    	SagaPlayer sagaPlayer = Saga.plugin().getLoadedPlayer(player.getName());
    	if(sagaPlayer == null){
    		SagaLogger.severe(BlockListener.class, "can't continue with onProjectileHit, because the saga player for "+ player.getName() + " isn't loaded.");
    		return;
    	}

		
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onEntityExplode(EntityExplodeEvent event) {
		
		
		// Stop creeper terrain damage.
		if(GeneralConfiguration.config().stopCreeperExplosions && event.getEntity() instanceof Creeper){
			event.blockList().clear();
		}
		
		// Get saga chunk:
		SagaChunk sagaChunk = BundleManager.manager().getSagaChunk(event.getLocation());
		
		// Forward to saga chunk:
		if(sagaChunk != null) sagaChunk.onEntityExplode(event);
		
		
	}
	
	@EventHandler(priority = EventPriority.NORMAL)
	public void onEntityBlockForm(EntityBlockFormEvent event) {
		

		// Get saga chunk:
		SagaChunk sagaChunk = BundleManager.manager().getSagaChunk(event.getBlock().getLocation());
		
		// Forward to saga chunk:
		if(sagaChunk != null) sagaChunk.onEntityBlockForm(event);
		
		
	}
	
	@EventHandler(priority = EventPriority.NORMAL)
	public void onCreatureSpawn(CreatureSpawnEvent event) {
		
		
		LivingEntity entity = event.getEntity();
		
    	// Unnatural tag:
    	if(event.getSpawnReason() == SpawnReason.SPAWNER && !entity.hasMetadata(SpawnerTag.METADATA_KEY)){
    		entity.setMetadata(SpawnerTag.METADATA_KEY, SpawnerTag.METADATA_VALUE);
    	}
		
		// Get saga chunk:
		SagaChunk sagaChunk = BundleManager.manager().getSagaChunk(event.getLocation());
		
		// Forward to saga chunk:
		if(sagaChunk != null) sagaChunk.onCreatureSpawn(event);
		
		
	}
	
	@EventHandler(priority = EventPriority.NORMAL)
	public void onEntityDeath(EntityDeathEvent event) {

		
		SagaEntityDeathEvent sagaEvent = new SagaEntityDeathEvent(event, event.getEntity());
		
		SagaPlayer sagaDead = null;
		SagaPlayer sagaAttacker = null;
		Creature deadCreature = null;
		
		if(sagaEvent.getLastDamageEvent() != null){
			sagaDead = sagaEvent.getLastDamageEvent().getDefenderPlayer();
			sagaAttacker = sagaEvent.getLastDamageEvent().getAttackerPlayer();
			deadCreature =  sagaEvent.getLastDamageEvent().getDefenderCreature();
		}
		
		
		// Player got killed by a player:
		if(sagaDead != null && sagaAttacker != null){
			
			// Get saga chunk:
			Location location = sagaAttacker.getLocation();
			Chunk chunk = location.getWorld().getChunkAt(location);
			SagaChunk sagaChunk = BundleManager.manager().getSagaChunk(chunk);
			
			// Forward to chunk:
			if(sagaChunk != null) sagaChunk.onPvpKill(sagaAttacker, sagaDead);
			
			// Forward to faction:
			Faction attackerFaction = sagaAttacker.getFaction();
			Faction deadFaction = sagaDead.getFaction();
			if(attackerFaction != null) attackerFaction.onPvpKill(sagaAttacker, sagaDead);
			if(deadFaction != null && deadFaction != attackerFaction) deadFaction.onPvpKill(sagaAttacker, sagaDead);
			
			
		}
		
		// Creature got killed by a player:
		else if(sagaAttacker != null && deadCreature != null){
			
		}
		
		// Player got killed:
		if(sagaDead != null){
			
			// Guardian rune:
			GuardianRune rune = sagaDead.getGuardRune();
			if(rune.isEnabled()) GuardianRune.handleAbsorb(sagaDead, event);
			
		}
		
		// Apply event:
		sagaEvent.apply();
		
		
	}


}
