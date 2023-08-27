package com.rs.game.npc.others;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.rs.Settings;
import com.rs.cache.loaders.ItemConfig;
import com.rs.discord.Bot;
import com.rs.executor.GameExecutorManager;
import com.rs.game.Animation;
import com.rs.game.World;
import com.rs.game.WorldTile;
import com.rs.game.item.FloorItem;
import com.rs.game.item.Item;
import com.rs.game.npc.NPC;
import com.rs.game.player.Player;
import com.rs.game.player.Skills;
import com.rs.game.player.content.RunePouch;
import com.rs.game.player.content.grandExchange.GrandExchange;
import com.rs.net.decoders.handlers.ButtonHandler;
import com.rs.utils.Logger;
import com.rs.utils.Utils;

@SuppressWarnings("serial")
public class GraveStone extends NPC {// 652 - gravestone selection interface

	private static final int GRAVE_STONE_INTERFACE = 266;

	private static final List<GraveStone> gravestones = new ArrayList<GraveStone>();

	public static GraveStone getGraveStoneByUsername(String username) {
		for (GraveStone stone : gravestones)
			if (stone.username.equals(username))
				return stone;
		return null;
	}

	private String username;
	private int ticks;
	private String inscription;
	private List<FloorItem> floorItems;
	private int graveStone;
	private boolean blessed;
	private int hintIcon;

	public GraveStone(Player player, WorldTile deathTile, Item[] items) {
		super(getNPCId(player.getGraveStone()), deathTile, -1, true);
		graveStone = player.getGraveStone();
		setDirection(Utils.getAngle(0, 1/*-1*/));
		setNextAnimation(new Animation(graveStone == 1 ? 7396 : 7394));
		username = player.getUsername();
		double multiplier = ((double)player.getDonator())/10 + 1;
		ticks = (int) (getMaximumTicks(graveStone) * multiplier);
		inscription = getInscription(player.getDisplayName());
		floorItems = new ArrayList<FloorItem>();
		for (Item item : items) {
			FloorItem i = World.addGroundItem(item, deathTile, player, true, -1, 2);
			Bot.sendLog(Bot.PICKUP_DROP_CHANNEL, "[type=DROP-GRAVESTONE][name="+player.getUsername()+"][item="+item.getName()+"("+item.getId()+")x"+Utils.getFormattedNumber(item.getAmount())+"]");
			if (i != null)
				floorItems.add(i);
		}
		synchronized (gravestones) {
			GraveStone oldStone = getGraveStoneByUsername(username);
			if (oldStone != null) {
				oldStone.addLeftTime(false);
				oldStone.finish();
			}
			gravestones.add(this);
		}
		player.getPackets().sendExecuteScript(2434, ticks);
		hintIcon = player.getHintIconsManager().addHintIcon(this, 0, -1, true);
		player.getPackets().sendCSVarInteger(623, deathTile.getTileHash());
		player.getPackets().sendCSVarInteger(624, 0);
		player.getPackets().sendCSVarString(53, "Your gravestone marker");
		player.getPackets().sendGameMessage("Your items have been dropped at your gravestone, where they'll remain until it crumbles. Look at the world map to help find your gravestone.");
		player.getPackets().sendGameMessage("It looks like it'll survive another " + (ticks / 100) + " minutes.");
	}

	@Override
	public void processNPC() {
		ticks--;
		if (ticks == 0)
			decrementGrave(-1, "Your grave has collapsed!");
		else if (ticks == 50)
			decrementGrave(2, "Your grave is collapsing.");
		else if (ticks == 100)
			decrementGrave(1, "Your grave is about to collapse.");
		else {
			//finish();
			checkDemolish();
		}
	}
	
	private void checkDemolish() {
		for (FloorItem item : floorItems) {
			if (World.containsItem(item))
				return;
		}
		finish(); //auto remove grave 
	}

	public void addLeftTime(boolean clean) {
		if (clean) {
			for (FloorItem item : floorItems)
				World.turnPublic(item, 100);
		} else {
			GameExecutorManager.slowExecutor.schedule(new Runnable() {
				@Override
				public void run() {
					try {
						for (FloorItem item : floorItems) {
							World.turnPublic(item, 100);
						}
					} catch (Throwable e) {
						Logger.handle(e);
					}
				}
			}, (long) (ticks * 0.6), TimeUnit.SECONDS);
		}
	}

	@Override
	public void finish() {
		synchronized (gravestones) {
			gravestones.remove(this);
		}
		Player player = getPlayer();
		if (player != null) {
			player.getPackets().sendExecuteScript(2434, 0);
			player.getHintIconsManager().removeHintIcon(hintIcon);
			player.getPackets().sendCSVarInteger(623, -1);

		}
		super.finish();
	}

	private Player getPlayer() {
		return World.getPlayer(username);
	}

	public void decrementGrave(int stage, String message) {
		Player player = getPlayer();
		if (player != null) {
			player.getPackets().sendGameMessage("<col=7E2217>" + message);
			player.getPackets().sendExecuteScript(2434, ticks);
		}
		if (stage == -1) {
			addLeftTime(true);
			finish();
		} else
			setNextNPCTransformation(getNPCId(graveStone) + stage);
	}

	public void sendGraveInscription(Player reader) {
		reader.getInterfaceManager().sendInterface(GRAVE_STONE_INTERFACE);
		reader.getVarsManager().sendVarBit(4191, graveStone == 0 ? 0 : 1);
		if (ticks <= 50) // if the grave is almost broken
			reader.getPackets().sendIComponentText(GRAVE_STONE_INTERFACE, 22, "The inscription is too unclear to read.");
		else if (reader == getPlayer())
			reader.getPackets().sendIComponentText(GRAVE_STONE_INTERFACE, 22, "It looks like it'll survive another " + (ticks / 100) + " minutes. Isn't there something a bit odd about reading your own gravestone?");
		else
			reader.getPackets().sendIComponentText(GRAVE_STONE_INTERFACE, 22, inscription);
	}
	
	public void lootAll(Player player) {
		if (!player.getUsername().equals(username)) {
			player.getPackets().sendGameMessage("You can only loot your own gravestone.");
			return;
		}
		boolean worn = false;
		for (FloorItem item : floorItems) {
			if (!World.containsItem(item))
				continue;
			if (!World.removeGroundItem(player, item))
				return;
			int slotID = player.getInventory().getItems().getThisItemSlot(item.getId());
			if (item.getDefinitions().isWearItem(player.getAppearence().isMale()) && ButtonHandler.sendWear2(player, slotID, item.getId()))
				worn = true;
			Bot.sendLog(Bot.PICKUP_DROP_CHANNEL, "[type=PICKUP-GRAVE-ALL][name="+player.getUsername()+"][from="+item.getOwner()+"][item="+item.getName()+"("+item.getId()+")x"+Utils.getFormattedNumber(item.getAmount())+"]");
			Logger.globalLog(player.getUsername(), player.getSession().getIP(), new String(" has picked up gravestone item [ id: " + item.getId() + ", amount: " + item.getAmount()));
		}
		if (worn) {
			player.getAppearence().generateAppearenceData();
			player.getInventory().getItems().shift();
			player.getInventory().refresh();
		}
		
	}

	public void repair(Player blesser, boolean bless) {
		if (blesser.getSkills().getLevel(Skills.PRAYER) < (bless ? 70 : 2)) {
			blesser.getPackets().sendGameMessage("You need " + (bless ? 70 : 2) + " prayer to " + (bless ? "bless" : "repair") + " a gravestone.");
			return;
		}
		if (blesser.getUsername().equals(username)) {
			blesser.getPackets().sendGameMessage("The gods don't seem to approve of people attempting to " + (bless ? "bless" : "repair") + " their own gravestones.");
			return;
		}
		if (bless && blessed) {
			blesser.getPackets().sendGameMessage("This gravestone has already been blessed.");
			return;
		} else if (!bless && ticks > 100) {
			blesser.getPackets().sendGameMessage("This gravestone doesn't seem to need repair.");
			return;
		}
		ticks += bless ? 6000 : 500; // 5minutes, 1hour
		blessed = true;
		decrementGrave(0, blesser.getDisplayName() + "has " + (bless ? "blessed" : "repaired") + " your gravestone. It should survive another " + (ticks / 100) + " minutes.");
		blesser.getPackets().sendGameMessage("You " + (bless ? "bless" : "repair") + " the grave.");
		blesser.lock(2);
		blesser.setNextAnimation(new Animation(645));
	}

	public void demolish(Player demolisher) {
		if (!demolisher.getBank().hasVerified(10))
			return;
		if (!demolisher.getUsername().equals(username)) 
			confirmDemolish(demolisher);
		else
			demolisher.getDialogueManager().startDialogue("DemolishD", this);
	}
	
	public void confirmDemolish(Player demolisher) {
		if (hasFinished())
			return;
		if (!demolisher.getUsername().equals(username)) {
			demolisher.getPackets().sendGameMessage("It would be impolite to demolish someone else's gravestone.");
			return;
		}
		addLeftTime(true);
		finish();
		demolisher.getPackets().sendGameMessage("It looks like it'll survive another " + (ticks / 100) + " minutes. You demolish it anyway.");
	}

	public static int getMaximumTicks(int graveStone) {
		switch (graveStone) {
		case 0:
			return 500 * 3;
		case 1:
		case 2:
			return 600 * 3;
		case 3:
			return 800 * 3;
		case 4:
		case 5:
		case 6:
		case 7:
		case 8:
		case 9:
		case 10:
		case 11:
			return 1000 * 3;
		case 12:
			return 1200 * 3;
		case 13:
			return 1500 * 3;
		}
		return 500  * 3;
	}

	private String getInscription(String username) {
		username = Utils.formatPlayerNameForDisplay(username);
		switch (graveStone) {
		case 0:
		case 1:
			return "In memory of <i>" + username + "</i>,<br>who died here.";
		case 2:
		case 3:
			return "In loving memory of our dear friend <i>" + username + "</i>,who <br>died in this place @X@ minutes ago.";
		case 4:
		case 5:
			return "In your travels, pause awhile to remember <i>" + username + "</i>,<br>who passed away at this spot.";
		case 6:
			return "<i>" + username + "</i>, <br>an enlightened servant of Saradomin,<br>perished in this place.";
		case 7:
			return "<i>" + username + "</i>, <br>a most bloodthirsty follower of Zamorak,<br>perished in this place.";
		case 8:
			return "<i>" + username + "</i>, <br>who walked with the Balance of Guthix,<br>perished in this place.";
		case 9:
			return "<i>" + username + "</i>, <br>a vicious warrior dedicated to Bandos,<br>perished in this place.";
		case 10:
			return "<i>" + username + "</i>, <br>a follower of the Law of Armadyl,<br>perished in this place.";
		case 11:
			return "<i>" + username + "</i>, <br>servant of the Unknown Power,<br>perished in this place.";
		case 12:
			return "Ye frail mortals who gaze upon this sight, forget not<br>the fate of <i>" + username + "</i>, once mighty, now surrendered to the inescapable grasp of destiny.<br><i>Requiescat in pace.</i>";
		case 13:
			return "Here lies <i>" + username + "</i>, friend of dwarves. Great in<br>life, glorious in death. His/Her name lives on in<br>song and story.";
		}
		return "Cabbage";
	}

	/*
	 * the final items after swaping slots
	 * return keptItems, dropedItems
	 * as we reset inv and equipment all others will just disapear
	 */
	public static Item[][] getItemsKeptOnDeath(Player player, Integer[][] slots) {
		ArrayList<Item> droppedItems = new ArrayList<Item>();
		ArrayList<Item> keptItems = new ArrayList<Item>();
		for (int i : slots[0]) { //items kept on death
			Item item = i >= 16 ? player.getInventory().getItem(i - 16) : player.getEquipment().getItem(i - 1);
			if (item == null || item.getId() == 41941 || item.getId() == RunePouch.ID) //shouldn't
				continue;
			item = new Item(item.getId(), item.getAmount());
			if (item.getAmount() > 1) {
				droppedItems.add(new Item(item.getId(), item.getAmount() - 1));
				item.setAmount(1);
			}
			keptItems.add(item);
		}
		for (int i : slots[1]) { //items droped on death
			Item item = i >= 16 ? player.getInventory().getItem(i - 16) : player.getEquipment().getItem(i - 1);
			if (item == null || item.getId() == 41941 || item.getId() == RunePouch.ID) //shouldnt
				continue;
			item = new Item(item.getId(), item.getAmount());
			droppedItems.add(item);
		}
		for (int i : slots[2]) { //items protected by default
			Item item = i >= 16 ? player.getInventory().getItem(i - 16) : player.getEquipment().getItem(i - 1);
			if (item == null || item.getId() == 41941/* || item.getId() == RunePouch.ID*/) //shouldnt
				continue;
			item = new Item(item.getId(), item.getAmount());
			keptItems.add(item);
		}
		return new Item[][]
		{ keptItems.toArray(new Item[keptItems.size()]), droppedItems.toArray(new Item[droppedItems.size()]) };

	}

	/*
	 * return arrays: items kept on death by default, items dropped on death by default, items protected by default, items lost by default
	 */
	public static Integer[][] getItemSlotsKeptOnDeath(final Player player, boolean atWilderness, boolean skulled, boolean protectPrayer) {
		ArrayList<Integer> droppedItems = new ArrayList<Integer>();
		ArrayList<Integer> protectedItems = atWilderness ? null : new ArrayList<Integer>();
		ArrayList<Integer> lostItems = new ArrayList<Integer>();
		for (int i = 1; i < 44; i++) {
			Item item = i >= 16 ? player.getInventory().getItem(i - 16) : player.getEquipment().getItem(i - 1);
			if (item == null)
				continue;
			int stageOnDeath = item.getDefinitions().getStageOnDeath();
			if (!atWilderness && stageOnDeath == 1)
				protectedItems.add(i);
			else if (stageOnDeath == -1)
				lostItems.add(i);
			else
				droppedItems.add(i);
		}
		int keptAmount = skulled ? 0 : 3;
		if (protectPrayer || (!atWilderness && player.isExtremeDonator()))
			keptAmount++;
		if (droppedItems.size() < keptAmount)
			keptAmount = droppedItems.size();
		Collections.sort(droppedItems, new Comparator<Integer>() {
			@Override
			public int compare(Integer o1, Integer o2) {
				Item i1 = o1 >= 16 ? player.getInventory().getItem(o1 - 16) : player.getEquipment().getItem(o1 - 1);
				Item i2 = o2 >= 16 ? player.getInventory().getItem(o2 - 16) : player.getEquipment().getItem(o2 - 1);
				int price1 = i1 == null ? 0 : Settings.USE_GE_PRICES_FOR_ITEMS_KEPT_ON_DEATH ? GrandExchange.getPrice(i1.getId()) : getDeathValue(i1.getId());
				int price2 = i2 == null ? 0 : Settings.USE_GE_PRICES_FOR_ITEMS_KEPT_ON_DEATH ? GrandExchange.getPrice(i2.getId()) : getDeathValue(i2.getId());
				if (price1 > price2)
					return -1;
				if (price1 < price2)
					return 1;
				return 0;
			}

		});
		Integer[] keptItems = new Integer[keptAmount];
		for (int i = 0; i < keptAmount; i++)
			keptItems[i] = droppedItems.remove(0);
		return new Integer[][]
		{
			keptItems,
			droppedItems.toArray(new Integer[droppedItems.size()]),
			atWilderness ? new Integer[0] : protectedItems.toArray(new Integer[protectedItems.size()]),
			atWilderness ? new Integer[0] : lostItems.toArray(new Integer[lostItems.size()]) };

	}
	
	public static int getDeathValue(int id) {
		ItemConfig defs = ItemConfig.forID(id);
		if (defs.isNoted())
			id = defs.getCertId();
		else if (defs.isLended())
			id = defs.getLendId();
		
		if (id >= 15441 && id <= 15444) //colored whips
			id = 4151;
		else if (id >= 15701 && id <= 15704) //colored dbow
			id = 11235; 
		else if (id >= 22207 && id <= 22214) //colored sol
			id = 15486; 
		else if (id >= 20949 && id <= 20952) //colored robin hood hat
			id = 2581; 
		else if (id == 14684) //zanik cbow
			id = 9183;
		
		if (id == 15432 || id == 15433) //nomad cape, cost 100k but act as 65k
			return 65000;
		else if (id == 6570) //fire cape
			return 70000;
		else if (id >= 18349 && id <= 18364) //chaotics
			return 20000000;
		else if (id == 14484 || id == 23695 || id == 25619 || id == 25633) //dclaws
			return 300000;
		else if (id == 18335) //arcane stream neck
			return 100000;
		else if (id == 7462 || id == 7461) //barrow & drag gloves
			return 65000;
		else if (id == 4585 || id == 4587) // d legs
			return 70000;
		else if (id == 10499)
			return 7500;
		else if (id == 20068)
			return 10000;
		return ItemConfig.forID(id).value;
	}

	public static int getNPCId(int currentGrave) {
		if (currentGrave == 13)
			return 13296;
		return 6565 + (currentGrave * 3);
	}
}