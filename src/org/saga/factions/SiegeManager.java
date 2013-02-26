package org.saga.factions;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Map.Entry;
import java.util.Set;

import org.bukkit.craftbukkit.libs.com.google.gson.JsonParseException;
import org.saga.Clock;
import org.saga.Clock.SecondTicker;
import org.saga.SagaLogger;
import org.saga.config.FactionConfiguration;
import org.saga.messages.FactionMessages;
import org.saga.player.SagaPlayer;
import org.saga.saveload.Directory;
import org.saga.saveload.WriterReader;
import org.saga.settlements.Bundle;
import org.saga.settlements.BundleManager;

public class SiegeManager implements SecondTicker{


	/**
	 * Instance of the manager.
	 */
	transient private static SiegeManager instance;

	/**
	 * Gets the manager.
	 * 
	 * @return manager
	 */
	public static SiegeManager manager() {
		return instance;
	}

	
	/**
	 * Faction declared sieges.
	 */
	private Hashtable<Integer, Integer> declaredSieges;

	/**
	 * Faction declared sieges.
	 */
	private Hashtable<Integer, Long> declaredDates;
	
	
	/**
	 * Faction siege progresses.
	 */
	private Hashtable<Integer, Double> siegeProgresses;
	
	/**
	 * Bundle owning faction.
	 */
	private Hashtable<Integer, Integer> owningFaction;
	
	/**
	 * Attackers counts.
	 */
	transient private Hashtable<Integer, Integer> attackers;
	
	/**
	 * Defender counts.
	 */
	transient private Hashtable<Integer, Integer> defenders;
	
	
	
	// Initialisation:
	/**
	 * Initialises the manager.
	 * 
	 * @param name nothing, prevents gson
	 */
	public SiegeManager(String name) {
		
		declaredSieges = new Hashtable<Integer, Integer>();
		declaredDates = new Hashtable<Integer, Long>();
		siegeProgresses = new Hashtable<Integer, Double>();
		owningFaction = new Hashtable<Integer, Integer>();
		
		attackers = new Hashtable<Integer, Integer>();
		defenders = new Hashtable<Integer, Integer>();
		
	}

	/**
	 * Fixes all problematic fields.
	 * 
	 */
	public void complete() {
		

		if(declaredSieges == null){
			SagaLogger.nullField(getClass(), "declaredSieges");
			declaredSieges = new Hashtable<Integer, Integer>();
		}
		
		if(declaredDates == null){
			SagaLogger.nullField(getClass(), "declaredDates");
			declaredDates = new Hashtable<Integer, Long>();
		}

		if(siegeProgresses == null){
			SagaLogger.nullField(getClass(), "siegeProgresses");
			siegeProgresses = new Hashtable<Integer, Double>();
		}
		
		if(owningFaction == null){
			SagaLogger.nullField(getClass(), "owningFaction");
			owningFaction = new Hashtable<Integer, Integer>();
		}
		
		
		// Remove unused declarations:
		Set<Integer> bundleIDs = declaredSieges.keySet();
		for (Integer bundleID: bundleIDs) {
			
			Integer factionID = declaredSieges.get(bundleID);
			Long date = declaredDates.get(bundleID);
			
			// Check declarations and dates:
			if(factionID == null) continue;
			if(date == null){
				SagaLogger.severe(getClass(), "siege declaration and date not synchronised");
				SagaLogger.info(getClass(), "removing declaration for ID " + factionID);
				declaredSieges.remove(factionID);
				continue;
			}

			// Already owning:
			Integer owningID = owningFaction.get(bundleID);
			if(owningID != null && owningID.equals(factionID)){
				declaredSieges.remove(bundleID);
				declaredDates.get(bundleID);
				SagaLogger.severe(getClass(), "sieged bundle with ID " + bundleID + " is already owned by faction ID " + factionID);
			}
			
			
		}
		
		// Transient:
		attackers = new Hashtable<Integer, Integer>();
		defenders = new Hashtable<Integer, Integer>();
		
		
	}
	
	
	
	// Declaration:
	public void handleDeclaration(Integer factionID, Integer bundleID) {
		
		
		declaredSieges.put(bundleID, factionID);
		declaredDates.put(bundleID, System.currentTimeMillis());
		
		
	}

	/**
	 * Calculates the minutes passed from declaration.
	 * 
	 * @param declared declared time in milliseconds
	 * @return minutes passed from declaration
	 */
	public static int calcPassedMinutes(Long declared) {
		return (int) ((System.currentTimeMillis() - declared)/60000);
	}

	/**
	 * Calculates the minutes passed from declaration.
	 * 
	 * @param declared declared time in milliseconds
	 * @return minutes passed from declaration
	 */
	public static int calcPassedSeconds(Long declared) {
		return (int) ((System.currentTimeMillis() - declared)/1000);
	}
	
	
	
	// Reminder:
	/**
	 * Handles siege reminding.
	 * 
	 * @param bundleID bundle ID
	 */
	private void handleRemind(Integer bundleID) {
		

		// Remaining minutes:
		Integer minutes = getSiegeRemainingMinutes(bundleID);
		if(minutes == null){
			SagaLogger.severe(getClass(), "missing passed siege declaration minutes for bundle ID " + bundleID);
			return;
		}
		
		// In progress reminder:
		if(minutes <= 0){
			if(-minutes % FactionConfiguration.config().getSiegeInProgressRemindIntervalMinutes() != 0) return;
		}
		
		// Short reminder:
		else if(minutes <= FactionConfiguration.config().getSiegeShortRemindStartMinutes()){
			if(minutes % FactionConfiguration.config().getSiegeShortRemindIntervalMinutes() != 0) return;
		}
		
		// Long reminder:
		else{
			if(minutes % FactionConfiguration.config().getSiegeLongRemindIntervalMinutes() != 0) return;
		}
		
		// Main variables:
		Faction attacker = getAttackingFaction(bundleID);
		if(attacker == null) return;
		
		Faction defender = getOwningFaction(bundleID);
		
		Bundle bundle = BundleManager.manager().getBundle(bundleID);
		if(bundle == null){
			SagaLogger.severe(getClass(), "missing bundle for bundle ID " + bundleID);
			return;
		}
		
		// Remind:
		attacker.information(FactionMessages.siegeAttackReminder(attacker, bundle));
		if(defender != null) defender.information(FactionMessages.siegeDefendReminder(defender, bundle));
		
		
	}
	
	// Date:
	/**
	 * Gets remaining until the siege. Minutes until start (negative) if not started.
	 * 
	 * @param factionID faction ID
	 * @param bundleID bundle ID
	 * @return elapsed minutes, negative is not started, null if not declared
	 */
	public Integer getSiegeRemainingMinutes(Integer bundleID) {
		
		
		Integer declaredFactionID = declaredSieges.get(bundleID);
		if(declaredFactionID == null) return null;
		
		Long date = declaredDates.get(bundleID);
		if(date == null){
			SagaLogger.severe(getClass(), "missing declaration date for bundle ID " + bundleID);
			SagaLogger.info(getClass(), "removing declaration");
			declaredSieges.remove(bundleID);
			return null;
		}
		
		int prepareMinutes = FactionConfiguration.config().getSiegePrepareMinutes();
		return prepareMinutes - calcPassedMinutes(date);
		
		
	}
	
	
	
	// Sieges:
	/**
	 * Handles siege tick.
	 * 
	 * @param attackerID attacking faction
	 * @param bundleID sieged bundle
	 */
	private void handleSiegeTick(Integer attackerID, Integer bundleID) {
		

		Integer defenderID = owningFaction.get(bundleID);
		
		// Get members:
		Collection<SagaPlayer> attakcerMembers = findSiegingMembers(attackerID, bundleID);
		Collection<SagaPlayer> defenderMembers;
		if(defenderID != null) defenderMembers = findSiegingMembers(defenderID, bundleID);
		else defenderMembers = new ArrayList<SagaPlayer>();
		
		// Set counts:
		attackers.put(bundleID, attakcerMembers.size());
		defenders.put(bundleID, defenderMembers.size());
		
		// Progress:
		int difference = attakcerMembers.size() - defenderMembers.size();
		double siegePts = FactionConfiguration.config().getSiegePtsPerSecond(difference);
		Double progress = modSiegeProgress(bundleID, siegePts);
		
		// Attacker success:
		if(progress >= 1.0){
			handleSiegeSucess(bundleID, attackerID);
		}
		else if(progress <= -1.0){
			handleSiegeFailure(bundleID, attackerID);
		}
		
		
	}

	/**
	 * Get members that are sieging a bundle.
	 * 
	 * @param factionID faction ID
	 * @param bundleID bundle ID
	 * @return sieging members
	 */
	public Collection<SagaPlayer> findSiegingMembers(Integer factionID, Integer bundleID) {
		
		
		ArrayList<SagaPlayer> sieging = new ArrayList<SagaPlayer>();
		
		// Get faction:
		Faction faction = FactionManager.manager().getFaction(factionID);
		if(faction == null){
			SagaLogger.severe(getClass(), "failed to retrieve faction for ID " + factionID);
			return new ArrayList<SagaPlayer>(0);
		}
		
		// Find sieging members:
		Collection<SagaPlayer> members = faction.getOnlineMembers();
		
		for (SagaPlayer sagaPlayer : members) {
			
			// Not in a bundle:
			if(sagaPlayer.lastSagaChunk == null) continue;
			
			// Not in the siege bundle:
			if(!sagaPlayer.lastSagaChunk.getBundle().getId().equals(bundleID)) continue;
			
			// On the border:
			if(sagaPlayer.lastSagaChunk.isBorder()) continue;
			
			sieging.add(sagaPlayer);
			
		}

		return sieging;
		
		
	}
	
	/**
	 * Gets the siege progress.
	 * 
	 * @param bundleID bundle ID
	 * @return progress
	 */
	public Double getSiegeProgress(Integer bundleID) {
		Double progress = siegeProgresses.get(bundleID);
		if(progress == null) progress = 0.0;
		return progress;
	}
	
	/**
	 * Modifies siege progress.
	 * 
	 * @param bundleID bundle ID
	 * @param mod amount to modify
	 * @return resulting amount
	 */
	public Double modSiegeProgress(Integer bundleID, Double mod) {
		
		Double amount = getSiegeProgress(bundleID) + mod;
		siegeProgresses.put(bundleID, amount);
		
		return amount;
		
	}
	
	
	/**
	 * Gets the bundle IDs the faction is attacking.
	 * 
	 * @param factionID faction ID
	 * @return attack bundle IDs
	 */
	public ArrayList<Integer> getDeclaredSiegesAttackIDs(Integer factionID) {
		
		ArrayList<Integer> bundleIDs = new ArrayList<Integer>();
		Set<Entry<Integer, Integer>> declarations = declaredSieges.entrySet();
		
		for (Entry<Integer, Integer> declaration : declarations) {
			if(declaration.getValue().equals(factionID)) bundleIDs.add(declaration.getKey());
		}
		
		return bundleIDs;
		
	}
	
	/**
	 * Gets the bundles the faction is attacking.
	 * 
	 * @param factionID faction ID
	 * @return attack bundles
	 */
	public ArrayList<Bundle> getDeclaredSiegesAttack(Integer factionID) {
		return BundleManager.manager().getBundles(getDeclaredSiegesAttackIDs(factionID));
	}

	/**
	 * Gets the bundle IDs the faction is defending.
	 * 
	 * @param factionID faction ID
	 * @return defend bundle IDs
	 */
	public ArrayList<Integer> getDeclaredSiegesDefendIDs(Integer factionID) {
		
		ArrayList<Integer> bundleIDs = new ArrayList<Integer>();
		
		ArrayList<Integer> ownedBundleIDs = getOwnedBundleIDs(factionID);
		for (Integer owneBundleID : ownedBundleIDs) {
			
			Integer declaringFactionID = declaredSieges.get(owneBundleID);
			if(declaringFactionID == null) continue;
			
			bundleIDs.add(owneBundleID);
			
		}
		
		return bundleIDs;
		
	}
	

	/**
	 * Gets the bundles the faction is defending.
	 * 
	 * @param factionID faction ID
	 * @return defend bundles
	 */
	public ArrayList<Bundle> getDeclaredSiegesDefend(Integer factionID) {
		return BundleManager.manager().getBundles(getDeclaredSiegesDefendIDs(factionID));
	}
	
	
	/**
	 * Gets all bundle IDs owned by the faction.
	 * 
	 * @param factionID faction ID
	 * @return owned bundle IDs
	 */
	public ArrayList<Integer> getOwnedBundleIDs(Integer factionID) {
		
		ArrayList<Integer> bundleIDs = new ArrayList<Integer>();
		
		Set<Entry<Integer, Integer>> owned = owningFaction.entrySet();
		for (Entry<Integer, Integer> owner : owned) {
			if(owner.getValue().equals(factionID)) bundleIDs.add(owner.getKey());
		}
		
		return bundleIDs;
		
	}

	/**
	 * Gets all bundles owned by the faction.
	 * 
	 * @param factionID faction ID
	 * @return owned bundles
	 */
	public ArrayList<Bundle> getOwnedBundles(Integer factionID) {
		return BundleManager.manager().getBundles(getOwnedBundleIDs(factionID));
	}
	
	
	
	// Attackers and defenders:
	/**
	 * Gets the number of attackers during a siege.
	 * 
	 * @param bundleID bundle ID
	 * @return number of attackers
	 */
	public int getAttackerCount(Integer bundleID) {
		
		Integer count = attackers.get(bundleID);
		if(count == null) return 0;
		return count;
		
	}
	
	/**
	 * Gets the number of defenders during a siege.
	 * 
	 * @param bundleID bundle ID
	 * @return number of defenders
	 */
	public int getDefenderCount(Integer bundleID) {
		
		Integer count = defenders.get(bundleID);
		if(count == null) return 0;
		return count;
		
	}
	
	
	
	// Concluding:
	/**
	 * Handles siege success.
	 * 
	 * @param bundleID bundle ID
	 * @param attackerID attacker faction ID
	 */
	private void handleSiegeSucess(Integer bundleID, Integer attackerID) {
		
		
		Integer defenderID = owningFaction.get(bundleID);
		Faction defender = null;
		
		// Remove progress:
		siegeProgresses.remove(bundleID);
		
		declaredSieges.remove(bundleID);
		declaredDates.remove(bundleID);
		
		attackers.remove(bundleID);
		defenders.remove(bundleID);
		
		// Set owner:
		owningFaction.put(bundleID, attackerID);
		
		// Involved parties:
		Faction attacker = FactionManager.manager().getFaction(attackerID);
		if(defenderID != null) defender = FactionManager.manager().getFaction(defenderID);
		Bundle bundle = BundleManager.manager().getBundle(bundleID);
		
		// Inform:
		attacker.information(FactionMessages.siegeAttackSuccess(attacker, bundle));
		if(defender != null) defender.information(FactionMessages.siegeDefendFailure(defender, bundle));
		
		
	}
	
	/**
	 * Handles siege failure.
	 * 
	 * @param bundleID bundle ID
	 * @param attackerID attacker faction ID
	 */
	private void handleSiegeFailure(Integer bundleID, Integer attackerID) {
		
		
		Integer defenderID = owningFaction.get(bundleID);
		Faction defender = null;
		
		// Remove progress:
		siegeProgresses.remove(bundleID);
		
		declaredSieges.remove(bundleID);
		declaredDates.remove(bundleID);
		
		attackers.remove(bundleID);
		defenders.remove(bundleID);
		
		// Get involved:
		Faction attacker = FactionManager.manager().getFaction(attackerID);
		if(defenderID != null) defender = FactionManager.manager().getFaction(defenderID);
		Bundle bundle = BundleManager.manager().getBundle(bundleID);
		
		// Inform:
		attacker.information(FactionMessages.siegeAttackFailure(attacker, bundle));
		if(defender != null) defender.information(FactionMessages.siegeDefendSuccess(defender, bundle));
		
	}
	
	
	
	// Attacker and defender:
	/**
	 * Gets the owning faction ID.
	 * 
	 * @param bundleID bundle ID
	 * @return owning faction ID
	 */
	public Integer getOwningID(Integer bundleID) {
		return owningFaction.get(bundleID);
	}
	
	/**
	 * Gets the owning faction.
	 * 
	 * @param bundleID bundle ID
	 * @return owning faction
	 */
	public Faction getOwningFaction(Integer bundleID) {
		Integer id = getOwningID(bundleID);
		if(id == null) return null;
		return FactionManager.manager().getFaction(id);
	}
	
	/**
	 * Gets the attacking faction ID.
	 * 
	 * @param bundleID bundle ID
	 * @return attacking faction ID
	 */
	public Integer getAttackingID(Integer bundleID) {
		return declaredSieges.get(bundleID);
	}
	
	/**
	 * Gets the attacking faction.
	 * 
	 * @param bundleID bundle ID
	 * @return attacking faction
	 */
	public Faction getAttackingFaction(Integer bundleID) {
		Integer id = getAttackingID(bundleID);
		if(id == null) return null;
		return FactionManager.manager().getFaction(id);
	}
	
	
	
	// Timing:
	/* 
	 * (non-Javadoc)
	 * 
	 * @see org.saga.Clock.MinuteTicker#clockMinuteTick()
	 */
	@Override
	public boolean clockSecondTick() {
		
		
		int prepareMinutes = FactionConfiguration.config().getSiegePrepareMinutes();
		
		Set<Entry<Integer, Integer>> declarations = declaredSieges.entrySet();
		
		for (Entry<Integer, Integer> declaration : declarations) {
			
			Integer factionID = declaration.getValue();
			Integer bundleID = declaration.getKey();
			
			Long declared = declaredDates.get(bundleID);
			if(declared == null){
				SagaLogger.severe(getClass(), "dates and declarations not synchronised for bundle ID " + bundleID);
				SagaLogger.info(getClass(), "removing declaration");
				declaredSieges.remove(bundleID);
				continue;
			}
			
			// Remind:
			int passedMinutes = calcPassedMinutes(declared);
			int passedSeconds = calcPassedSeconds(declared);
			if(passedMinutes*60 == passedSeconds) handleRemind(bundleID);
			
			// Check if begun:
			if(passedMinutes < prepareMinutes) continue;

			// Handle siege:
			handleSiegeTick(factionID, bundleID);
			
		}
		
		return true;

			
	}
	
	
	
	// Load unload:
	/**
	 * Loads the manager.
	 * 
	 * @return experience configuration
	 */
	public static SiegeManager load(){

		
		// Inform:
		SagaLogger.info("Loading sieges.");
		
		// New:
		if(!WriterReader.checkExists(Directory.WARS)){
			
			instance = new SiegeManager("");
			save();
        	
        }
		
		// Load:
		else{
			
			try {
				
				instance = WriterReader.read(Directory.WARS, SiegeManager.class);
				
			} catch (FileNotFoundException e) {
				
				instance = new SiegeManager("");
				
			} catch (IOException e) {
				
				SagaLogger.severe(SiegeManager.class, "failed to load");
				instance = new SiegeManager("");
				
			} catch (JsonParseException e) {
				
				SagaLogger.severe(SiegeManager.class, "failed to parse");
				SagaLogger.info("Parse message :" + e.getMessage());
				instance = new SiegeManager("");
				
			}
			
        }
		
		// Complete:
		instance.complete();
		
		// Clock:
		Clock.clock().enableSecondTick(instance);
		
		return instance;
		
		
	}

	/**
	 * Unloads the manager.
	 * 
	 */
	public static void unload(){

		
		// Inform:
		SagaLogger.info("Unloading sieges.");
		
		save();
		
		instance = null;
		
		
	}
	
	/**
	 * Saves the manager.
	 * 
	 */
	public static void save(){

		
		// Inform:
		SagaLogger.info("Saving faction claims.");
		
		try {
			
			WriterReader.write(Directory.WARS, instance);
			
		} catch (IOException e) {
			
			SagaLogger.severe(FileNotFoundException.class, "write failed");
			SagaLogger.info("Write failure cause:" + e.getClass().getSimpleName() + ":" + e.getMessage());
			
		}
		
	}
	
	
}