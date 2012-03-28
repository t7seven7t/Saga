package org.saga.buildings;

import java.util.ArrayList;
import java.util.Random;

import org.bukkit.Location;
import org.bukkit.entity.Creature;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.util.Vector;
import org.saga.Clock;
import org.saga.Clock.SecondTicker;
import org.saga.Saga;
import org.saga.SagaMessages;
import org.saga.chunkGroups.ChunkGroup;
import org.saga.chunkGroups.ChunkGroupManager;
import org.saga.chunkGroups.ChunkGroupMessages;
import org.saga.chunkGroups.SagaChunk;
import org.saga.listeners.events.SagaPvpEvent;
import org.saga.listeners.events.SagaPvpEvent.PvpDenyReason;
import org.saga.player.SagaPlayer;
import org.sk89q.Command;
import org.sk89q.CommandContext;
import org.sk89q.CommandPermissions;

public class TownSquare extends Building implements SecondTicker{

	
	/**
	 * Player will spawn at the town square if true.
	 */
	private Boolean spawnEnabled;
	
	/**
	 * Random generator.
	 */
	transient private Random random;
	
	/**
	 * True if is on cool down.
	 */
	transient boolean isOnCooldown;
	
	/**
	 * Seconds left for the cool down.
	 */
	transient int cooldownLeft;
	
	
	// Initialization:
	/**
	 * Used by gson.
	 * 
	 */
	protected TownSquare() {
		super();
	}
	
	/**
	 * Sets a name.
	 * 
	 * @param name name
	 */
	public TownSquare(String name) {
		
		super("");
		random = new Random();
		spawnEnabled = true;
		
	}

	/* 
	 * (non-Javadoc)
	 * 
	 * @see org.saga.buildings.Building#completeExtended()
	 */
	@Override
	public boolean completeExtended() {
		

		boolean integrity = true;
		
		// Fields:
		if(spawnEnabled == null){
			spawnEnabled = true;
			Saga.info("Failed to initialize spawnEnabled field for " + this + " building.");
			integrity = false;
		}
		
		// Transient:
		random = new Random();
		isOnCooldown = false;
		cooldownLeft = 0;
		
		return integrity;
		
		
	}

	/**
	 * Creates a new instance.
	 * 
	 * @return new instance
	 */
	public static TownSquare newInctance(){
		return new TownSquare("");
	}
	
	/* 
	 * (non-Javadoc)
	 * 
	 * @see org.saga.buildings.Building#duplicate()
	 */
	@Override
	public Building blueprint() {
		
		return new TownSquare("");
		
	}
	
	/* 
	 * (non-Javadoc)
	 * 
	 * @see org.saga.buildings.Building#disable()
	 */
	@Override
	public void disable() {
		
		
		super.disable();
		
		// Unregister if still on cool down:
		if (isOnCooldown) {
			Clock.clock().unregisterSecondTick(this);
		}
		
	}
	
	
	// Clock:
	/* 
	 * (non-Javadoc)
	 * 
	 * @see org.saga.Clock.SecondTicker#clockSecondTick()
	 */
	@Override
	public void clockSecondTick() {

		
		if(!isOnCooldown){
			return;
		}
		
		cooldownLeft --;
		
		if(cooldownLeft <= 0){
			stopCooldown();
		}
		
		
	}
	
	/**
	 * Starts cool down.
	 * 
	 */
	private void startCooldown() {

		
		isOnCooldown = true;
		
		cooldownLeft = getDefinition().getLevelFunction().value(getLevel()).intValue();
		
		Clock.clock().registerSecondTick(this);
		
		
	}
	
	/**
	 * Stops cool down.
	 * 
	 */
	private void stopCooldown() {

		
		isOnCooldown = false;
		
		cooldownLeft = 0;
		
		Clock.clock().unregisterSecondTick(this);
		
		
	}
	
	/**
	 * Gets the cool down.
	 * 
	 * @return gets cool down
	 */
	public int getCooldown() {
		return cooldownLeft;
	}
	
	/**
	 * Checks if on cool down.
	 * 
	 * @return true if on cool down
	 */
	public boolean isOnCooldown() {
		return isOnCooldown;
	}
	
	
	// Utility:
	/**
	 * Gets the spawn location on this chunk.
	 * 
	 * @return spawn location, null if not found
	 */
	public Location getSpawnLocation() {

		
		SagaChunk sagaChunk = getSagaChunk();
		if(sagaChunk == null){
			return null;
		}
		
		// Displacement:
		double spreadRadius = 6;
		Double x = 2 * spreadRadius * (random.nextDouble() - 0.5);
		Double z = 2 * spreadRadius * (random.nextDouble() - 0.5);
		Vector displacement = new Vector(x, 2, z);
		
		// Shifted location:
		Location spawnLocation = sagaChunk.getLocation(displacement);
		if(spawnLocation == null){
			return null;
		}
		
		if(spawnLocation.getY() < 10){
			Saga.severe(this, spawnLocation + " is an invalid spawn location", "ignoring location");
			return null;
		}
		
		return spawnLocation;
		
		
	}
	
	/**
	 * Prepares the chunk.
	 * 
	 */
	private void prepareChunk() {

		
		SagaChunk originChun = getSagaChunk();
		
		if(originChun == null){
			Saga.severe(this, "failed to retrieve origin chunk", "ignoring chunk refresh");
			return;
		}
		
		if(!originChun.isChunkLoaded()){
			originChun.loadChunk();
		}
		
		
	}
	
	
	// Events:
	/* 
	 * (non-Javadoc)
	 * 
	 * @see org.saga.buildings.Building#memberRespawnEvent(org.saga.SagaPlayer, org.bukkit.event.player.PlayerRespawnEvent)
	 */
	@Override
	public void onMemberRespawn(SagaPlayer sagaPlayer, PlayerRespawnEvent event) {

		
		// Location chunk:
		SagaChunk locationChunk = getSagaChunk();
		if(locationChunk == null){
			Saga.severe(this + " building can't continue with memberRespawnEvent, because the location chunk isn't set.");
			return;
		}
		
//		// Cool down;
//		if(isOnCooldown){
//			sagaPlayer.sendMessage(BuildingMessages.cooldown(this, cooldownLeft));
//			return;
//		}else{
//			startCooldown();
//		}

		// Prepare chunk:
		prepareChunk();
		
		Location spawnLocation = getSpawnLocation();
		
		if(spawnLocation == null){
			Saga.severe(this, "can't continue with onMemberRespawnEvent, because the location can't be retrieved","ignoring request");
			sagaPlayer.error("failed to respawn at " + getDisplayName());
			return;
		}
		
		// Respawn if enabled:
		if(spawnEnabled){
			event.setRespawnLocation(spawnLocation);
		}
		
	
	}
	
	/* 
	 * (non-Javadoc)
	 * 
	 * @see org.saga.buildings.Building#onPlayerDamagedByPlayer(org.bukkit.event.entity.EntityDamageByEntityEvent, org.saga.SagaPlayer, org.saga.SagaPlayer)
	 */
	@Override
	public void onPvP(SagaPvpEvent event){
		
		// Deny pvp:
		event.setDenyReason(PvpDenyReason.SAFE_AREA);
		
	}
	
	/* 
	 * (non-Javadoc)
	 * 
	 * @see org.saga.buildings.Building#onPlayerDamagedByCreature(org.bukkit.event.entity.EntityDamageByEntityEvent, org.bukkit.entity.Creature, org.saga.SagaPlayer)
	 */
	@Override
	public void onPlayerDamagedByCreature(EntityDamageByEntityEvent event, Creature damager, SagaPlayer damaged) {

		// Disable cvp:
		event.setCancelled(true);
		
	}
	
	// Commands:
	@Command(
            aliases = {"sspawn"},
            usage = "",
            flags = "",
            desc = "Spawn in your settlement town square.",
            min = 0,
            max = 1)
	@CommandPermissions({"saga.user.settlement.building.townsquare.spawn"})
	public static void spawn(CommandContext args, Saga plugin, SagaPlayer sagaPlayer) {

		
		ChunkGroup selectedChunkGroup = null;
		
		// Arguments:
		if(args.argsLength() == 1){
			
			// Chunk group:
			String groupName = args.getString(0).replaceAll(SagaMessages.spaceSymbol, " ");
			selectedChunkGroup = ChunkGroupManager.manager().getChunkGroupWithName(groupName);
			if(selectedChunkGroup == null){
				sagaPlayer.message(ChunkGroupMessages.noChunkGroup(groupName));
				return;
			}
			
		}else{
			
			// Chunk group:
			selectedChunkGroup = sagaPlayer.getRegisteredChunkGroup();
			if(selectedChunkGroup == null){
				sagaPlayer.message( ChunkGroupMessages.noChunkGroup() );
				return;
			}
			
		}
		
		// Permission:
		if( !selectedChunkGroup.canSpawn(sagaPlayer) ){
			sagaPlayer.message(SagaMessages.noPermission());
			return;
		}
		
		ArrayList<TownSquare> selectedBuildings = selectedChunkGroup.getBuildings(TownSquare.class);
		
		if(selectedBuildings.size() == 0){
			sagaPlayer.message(noTownSquare(selectedChunkGroup));
			return;
		}
		
		Integer smallestCooldown = Integer.MAX_VALUE;
		TownSquare selectedBuilding = null;
		
		for (TownSquare townSquare : selectedBuildings) {
			
			if(townSquare.getCooldown() < smallestCooldown) smallestCooldown = townSquare.getCooldown();
			
			if(!townSquare.isOnCooldown()){
				selectedBuilding = townSquare;
				break;
			}
			
		}
		
		// Everything on cool down:
		if(selectedBuilding == null){
			sagaPlayer.message(BuildingMessages.cooldown(Building.getName(TownSquare.class), smallestCooldown));
			return;
		}
		
		// Prepare chunk:
		selectedBuilding.prepareChunk();
		
		// Location:
		Location spawnLocation = selectedBuilding.getSpawnLocation();
		if(spawnLocation == null){
			Saga.severe(selectedBuilding, sagaPlayer + " player failed to respawn at " + selectedBuilding.getDisplayName(), "ignoring request");
			sagaPlayer.error("failed to respawn");
			return;
		}
		
		// Teleport:
		sagaPlayer.teleport(spawnLocation);
		
		// Cool down:
		selectedBuilding.startCooldown();
		
	
	}
	
	
	// Messages:
	public static String noTownSquare(ChunkGroup chunkGroup){
		
		return BuildingMessages.negative + "" + chunkGroup.getName() + " deosen't have a " + TownSquare.getName(TownSquare.class) + ".";
		
	}
	
	
	
}
