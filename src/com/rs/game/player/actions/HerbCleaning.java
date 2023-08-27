package com.rs.game.player.actions;

import java.util.LinkedList;
import java.util.List;

import com.rs.cache.loaders.ItemConfig;
import com.rs.game.item.Item;
import com.rs.game.player.Player;
import com.rs.game.player.Skills;
import com.rs.game.player.Achievements.Task;
import com.rs.game.tasks.WorldTask;
import com.rs.game.tasks.WorldTasksManager;

public class HerbCleaning {

	public static enum Herbs {

		GUAM(199, 2.5, 1, 249),

		MARRENTILL(201, 3.8, 5, 251),

		TARROMIN(203, 5, 11, 253),

		HARRALANDER(205, 6.3, 20, 255),

		RANARR(207, 7.5, 25, 257),

		TOADFLAX(3049, 8, 30, 2998),

		SPIRIT_WEED(12174, 7.8, 35, 12172),

		IRIT(209, 8.8, 40, 259),

		WERGALI(14836, 9.5, 41, 14854),

		AVANTOE(211, 10, 48, 261),

		KWUARM(213, 11.3, 54, 263),

		SNAPDRAGON(3051, 11.8, 59, 3000),

		CADANTINE(215, 12.5, 65, 265),

		LANTADYME(2485, 13.1, 67, 2481),

		DWARF_WEED(217, 13.8, 70, 267),

		TORSTOL(219, 15, 75, 269),

		FELLSTALK(21626, 190, 91, 21624),

		SAGEWORT(17494, 2.1, 3, 17512),

		VALERIAN(17496, 3.2, 4, 17514),

		ALOE(17498, 4, 8, 17516),

		WORMWOOD_LEAF(17500, 7.2, 34, 17518),

		MAGEBANE(17502, 7.7, 37, 17520),

		FEATHERFOIL(17504, 8.6, 41, 17522),

		GRIMY_WINTERS_GRIP(17506, 12.7, 67, 17524),

		LYCOPUS(17508, 13.1, 70, 17526),

		BUCKTHORN(17510, 13.8, 74, 17528);

		private int herbId;
		private int level;
		private int cleanId;
		private double xp;

		Herbs(int herbId, double xp, int level, int cleanId) {
			this.herbId = herbId;
			this.xp = xp;
			this.level = level;
			this.cleanId = cleanId;
		}

		public int getHerbId() {
			return herbId;
		}

		public double getExperience() {
			return xp;
		}

		public int getLevel() {
			return level;
		}

		public int getCleanId() {
			return cleanId;
		}
	}

	public static Herbs getHerb(int id) {
		for (final Herbs herb : Herbs.values())
			if (herb.getHerbId() == id)
				return herb;
		return null;
	}

	public static Herbs getHerbOrNoted(int id) {
		for (final Herbs herb : Herbs.values())
			if (herb.getHerbId() == id || ItemConfig.forID(herb.getHerbId()).cert == id)
				return herb;
		return null;
	}
	public static List<Herbs> getHerbs() {
		List<Herbs> herbs = new LinkedList<Herbs>();
		for (Herbs herb : Herbs.values()) {
			if (herb.ordinal() < 17) //Felstalks
				herbs.add(herb);
		}
		return herbs;
	}

	public static boolean clean(final Player player, Item item, final int slotId) {
		final Herbs herb = getHerb(item.getId());
		if (herb == null)
			return false;
		if (player.getSkills().getLevel(Skills.HERBLORE) < herb.getLevel()) {
			player.getPackets().sendGameMessage("You do not have the required level to clean this.");
			return true;
		}
		WorldTasksManager.schedule(new WorldTask() {
			@Override
			public void run() {
				Item item = player.getInventory().getItem(slotId);
				if (item == null || item.getId() != herb.getHerbId())
					return;
				player.getInventory().deleteItem(new Item(item.getId(), 1));
				player.getInventory().addItem(new Item(herb.getCleanId()));
				
				if (herb == Herbs.AVANTOE)
					player.getAchievements().add(Task.CLEAN_AVANTOE);
				
				player.getSkills().addXp(Skills.HERBLORE, herb.getExperience());
				player.getPackets().sendGameMessage("You clean the herb.", true);
			}

		});
		return true;
	}

}