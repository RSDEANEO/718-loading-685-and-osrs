package com.rs.game.player;

import java.util.Arrays;

import com.rs.discord.Bot;
import com.rs.game.item.Item;
import com.rs.game.item.ItemsContainer;
import com.rs.game.player.content.ItemConstants;
import com.rs.game.player.content.grandExchange.GrandExchange;
import com.rs.utils.ItemExamines;
import com.rs.utils.Logger;
import com.rs.utils.Utils;

public class Trade {

	private Player player, target;
	private ItemsContainer<Item> items;
	private boolean tradeModified;
	private boolean accepted;

	public ItemsContainer<Item> getItems() {
		return items;
	}

	public Trade(Player player) {
		this.player = player; // player reference
		items = new ItemsContainer<Item>(28, false);
	}

	/*
	 * called to both players
	 */
	public void openTrade(final Player target) {
		synchronized (this) {
			player.stopAll();
			Logger.globalLog(player.getUsername(), player.getSession().getIP(), new String(" began trading with " + target.getUsername() + "."));
			this.target = target;
			player.getPackets().sendIComponentText(335, 17, "Trading With: " + target.getDisplayName());
			player.getPackets().sendCSVarString(203, target.getDisplayName());
			sendInterItems();
			sendOptions();
			sendTradeModified();
			refreshFreeInventorySlots();
			refreshTradeWealth();
			refreshStageMessage(true);
			player.getInterfaceManager().sendInterface(335);
			player.getInterfaceManager().sendInventoryInterface(336);
			player.setCloseInterfacesEvent(new Runnable() {
				@Override
				public void run() {
					closeTrade(CloseTradeStage.CANCEL);
				}
			});
		}

	}

	public void removeItem(final int slot, int amount) {
		synchronized (this) {
			if (!isTrading())
				return;

			Item item = items.get(slot);
			if (item == null)
				return;
			Item[] itemsBefore = items.getItemsCopy();
			int maxAmount = items.getNumberOf(item);
			if (amount < maxAmount)
				item = new Item(item.getId(), amount);
			else
				item = new Item(item.getId(), maxAmount);
			items.remove(slot, item);
			player.getInventory().addItemMoneyPouch(item);
			refreshItems(itemsBefore);
			cancelAccepted();
			setTradeModified(true);
		}

	}

	public void sendFlash(int slot) {
		player.getPackets().sendInterFlashScript(335, 33, 4, 7, slot);
		target.getPackets().sendInterFlashScript(335, 36, 4, 7, slot);
	}

	public void cancelAccepted() {
		boolean canceled = false;
		if (accepted) {
			accepted = false;
			canceled = true;
		}
		if (target.getTrade().accepted) {
			target.getTrade().accepted = false;
			canceled = true;
		}
		if (canceled)
			refreshBothStageMessage(canceled);
	}
	
	public boolean addItem(Item item) {
		synchronized (this) {
			if (item == null || !isTrading())
				return false;
			if (!ItemConstants.isTradeable(item)) {
				player.getPackets().sendGameMessage("That item isn't tradeable.");
				return false;
			}

			int amountInTrade = getItems().getNumberOf(item);
			long totalAmount = (long) item.getAmount() + (long) amountInTrade;

			if(totalAmount > Integer.MAX_VALUE) {
				int allowed = Integer.MAX_VALUE - amountInTrade;
				item.setAmount(allowed);
				if(allowed <= 0) {
					player.sendMessage("The trade cannot hold any more " + item.getName() + ".");
					return false;
				}
			}

			long targetHeldAmount = target.getInventory().getAmountOf(item.getId());
			boolean isCoins = item.getId() == 995;
			/*if(isCoins)
				targetHeldAmount += target.getMoneyPouch().getCoinsAmount();*/
			if (!isCoins && targetHeldAmount > 0) {
				long targetAllowed = (isCoins ? (long) (Integer.MAX_VALUE)*2 : Integer.MAX_VALUE) - targetHeldAmount;
				if(targetAllowed < (long) item.getAmount() + amountInTrade) {
					player.sendMessage( target.getName() + (targetAllowed == 0 ? " cannot hold any more " + item.getName() + "." : " can only hold " + Utils.getFormattedNumber(targetAllowed) + " more " + item.getName()) + ".");
					return false;
				}
			}

			Item[] itemsBefore = items.getItemsCopy();
			items.add(item);
			refreshItems(itemsBefore);
			cancelAccepted();
			return true;
		}
	}

	public boolean addItem(int slot, int amount) {
		synchronized (this) {
			if (!isTrading())
				return false;

			Item item = player.getInventory().getItem(slot);
			if (item == null)
				return false;
			int maxAmount = player.getInventory().getItems().getNumberOf(item);
			if (amount < maxAmount)
				item = new Item(item.getId(), amount);
			else
				item = new Item(item.getId(), maxAmount);

			boolean success = addItem(item);
			if(success)
				player.getInventory().deleteItem(slot, item);
			return success;
		}

	}

	public void refreshItems(Item[] itemsBefore) {
		int[] changedSlots = new int[itemsBefore.length];
		int count = 0;
		for (int index = 0; index < itemsBefore.length; index++) {
			Item item = items.getItems()[index];
			if (itemsBefore[index] != item) {
				if (itemsBefore[index] != null && (item == null || item.getId() != itemsBefore[index].getId() || item.getAmount() < itemsBefore[index].getAmount()))
					sendFlash(index);
				changedSlots[count++] = index;
			}
		}
		int[] finalChangedSlots = new int[count];
		System.arraycopy(changedSlots, 0, finalChangedSlots, 0, count);
		refresh(finalChangedSlots);
		refreshFreeInventorySlots();
		refreshTradeWealth();
	}

	public void sendOptions() {
		player.getPackets().sendInterSetItemsOptionsScript(336, 0, 93, 4, 7, "Offer", "Offer-5", "Offer-10", "Offer-All", "Offer-X", "Value<col=FF9040>", "Lend");
		player.getPackets().sendIComponentSettings(336, 0, 0, 27, 1278);
		player.getPackets().sendInterSetItemsOptionsScript(335, 32, 90, 4, 7, "Remove", "Remove-5", "Remove-10", "Remove-All", "Remove-X", "Value");
		player.getPackets().sendIComponentSettings(335, 32, 0, 27, 1150);
		player.getPackets().sendInterSetItemsOptionsScript(335, 35, 90, true, 4, 7, "Value");
		player.getPackets().sendIComponentSettings(335, 35, 0, 27, 1026);

	}

	public boolean isTrading() {
		return target != null;
	}

	public void setTradeModified(boolean modified) {
		if (modified == tradeModified)
			return;
		tradeModified = modified;
		sendTradeModified();
	}

	public void sendInterItems() {
		player.getPackets().sendItems(90, items);
		target.getPackets().sendItems(90, true, items);
	}

	public void refresh(int... slots) {
		player.getPackets().sendUpdateItems(90, items, slots);
		target.getPackets().sendUpdateItems(90, true, items.getItems(), slots);
	}

	public void accept(boolean firstStage) {
		synchronized (this) {
			if (!isTrading())
				return;
			if (target.getTrade().accepted) {
				if (firstStage) {
					if (nextStage())
						target.getTrade().nextStage();
				} else {
					player.setCloseInterfacesEvent(null);
					player.closeInterfaces();
					closeTrade(CloseTradeStage.DONE);
				}
				return;
			}
			accepted = true;
			refreshBothStageMessage(firstStage);
		}
	}

	public void sendValue(int slot, boolean traders) {
		if (!isTrading())
			return;
		Item item = traders ? target.getTrade().items.get(slot) : items.get(slot);
		if (item == null)
			return;
		if (!ItemConstants.isTradeable(item)) {
			player.getPackets().sendGameMessage("That item isn't tradeable.");
			return;
		}
		int price = GrandExchange.getPrice(item.getId());
		player.getPackets().sendGameMessage(item.getDefinitions().getName() + ": market price is " + price + " coins.");
	}

	public void sendValue(int slot) {
		Item item = player.getInventory().getItem(slot);
		if (item == null)
			return;
		if (!ItemConstants.isTradeable(item)) {
			player.getPackets().sendGameMessage("That item isn't tradeable.");
			return;
		}
		int price = GrandExchange.getPrice(item.getId());
		player.getPackets().sendGameMessage(item.getDefinitions().getName() + ": market price is " + price + " coins.");
	}

	public void sendExamine(int slot, boolean traders) {
		if (!isTrading())
			return;
		Item item = traders ? target.getTrade().items.get(slot) : items.get(slot);
		if (item == null)
			return;
		player.getPackets().sendGameMessage(ItemExamines.getExamine(item));
	}

	public boolean nextStage() {
		if (!isTrading())
			return false;
		if (player.getInventory().getItems().getUsedSlots() + target.getTrade().items.getUsedSlots() > 28) {
			player.setCloseInterfacesEvent(null);
			player.closeInterfaces();
			closeTrade(CloseTradeStage.NO_SPACE);
			return false;
		}
		accepted = false;
		player.getInterfaceManager().sendInterface(334);
		player.getInterfaceManager().removeInventoryInterface();
		player.getPackets().sendHideIComponent(334, 55, !(tradeModified || target.getTrade().tradeModified));
		refreshBothStageMessage(false);
		return true;
	}

	public void refreshBothStageMessage(boolean firstStage) {
		synchronized (target.getTrade()) { //if deadlock happen within a few days, gottam ake sure to rechec this
			refreshStageMessage(firstStage);
			target.getTrade().refreshStageMessage(firstStage);
		}
	}

	public void refreshStageMessage(boolean firstStage) {
		player.getPackets().sendIComponentText(firstStage ? 335 : 334, firstStage ? 39 : 34, getAcceptMessage(firstStage));
	}

	public String getAcceptMessage(boolean firstStage) {
		if (accepted)
			return "Waiting for other player...";
		if (target.getTrade().accepted)
			return "Other player has accepted.";
		return firstStage ? "" : "Are you sure you want to make this trade?";
	}

	public void sendTradeModified() {
		player.getVarsManager().sendVar(1042, tradeModified ? 1 : 0);
		target.getVarsManager().sendVar(1043, tradeModified ? 1 : 0);
	}

	public void refreshTradeWealth() {
		int wealth = getTradeWealth();
		player.getPackets().sendCSVarInteger(729, wealth);
		target.getPackets().sendCSVarInteger(697, wealth);
	}

	public void refreshFreeInventorySlots() {
		int freeSlots = player.getInventory().getFreeSlots();
		target.getPackets().sendIComponentText(335, 23, "has " + (freeSlots == 0 ? "no" : freeSlots) + " free" + "<br>inventory slots");
	}

	public int getTradeWealth() {
		int wealth = 0;
		for (Item item : items.getItems()) {
			if (item == null)
				continue;
			wealth += GrandExchange.getPrice(item.getId()) * item.getAmount();
		}
		return wealth;
	}

	private static enum CloseTradeStage {
		CANCEL, NO_SPACE, DONE
	}

	public void reset() {
		target = null;
		tradeModified = false;
		accepted = false;
	}

	public void closeTrade(CloseTradeStage stage) {
		synchronized (this) {
			if (isTrading() && target.getTrade().isTrading()) {
				Player oldTarget = target;
				reset();
				oldTarget.getTrade().reset();
				oldTarget.setCloseInterfacesEvent(null);
				oldTarget.closeInterfaces();
				if (CloseTradeStage.DONE != stage) {
					for (Item item : items.getItems()) {
						if (item == null) continue;
						player.getInventory().addItemMoneyPouch(item);
					}
					for (Item item : oldTarget.getTrade().items.getItems()) {
						if (item == null) continue;
						oldTarget.getInventory().addItemMoneyPouch(item);
					}
					oldTarget.getTrade().items.clear();
					items.clear();
				} else {
					Logger.globalLog(player.getUsername(), player.getSession().getIP(), new String(" completed the trade with " + oldTarget.getUsername() + " items are as follows: " + Arrays.toString(items.getShiftedItem())));
					Logger.globalLog(oldTarget.getUsername(), oldTarget.getSession().getIP(), new String(" completed the trade with " + player.getUsername() + " items are as follows " + Arrays.toString(oldTarget.getTrade().items.getShiftedItem()) + "."));
					for (Item item : items.getItems()) {
						if (item == null) continue;
						oldTarget.getInventory().addItemMoneyPouch(item);
						Bot.sendLog(Bot.TRADE_STACK_CANNEL, "[type=TRADE][name="+player.getUsername()+"][to="+oldTarget.getUsername()+"][item="+item.getName()+"("+item.getId()+")x"+Utils.getFormattedNumber(item.getAmount())+"]");
					}
					for (Item item : oldTarget.getTrade().items.getItems()) {
						if (item == null) continue;
						player.getInventory().addItemMoneyPouch(item);
						Bot.sendLog(Bot.TRADE_STACK_CANNEL, "[type=TRADE][name="+oldTarget.getUsername()+"][to="+player.getUsername()+"][item="+item.getName()+"("+item.getId()+")x"+Utils.getFormattedNumber(item.getAmount())+"]");
					}
					oldTarget.getTrade().items.clear();
					items.clear();
					player.getPackets().sendGameMessage("Accepted trade.");
					oldTarget.getPackets().sendGameMessage("Accepted trade.");
				}
				Logger.globalLog(player.getUsername(), player.getSession().getIP(), new String(" trade between " + player.getUsername() + " and " + oldTarget.getUsername() + " has been finished."));
				if (CloseTradeStage.CANCEL == stage)
					oldTarget.getPackets().sendGameMessage("<col=ff0000>Other player declined trade!");
				else if (CloseTradeStage.NO_SPACE == stage) {
					player.getPackets().sendGameMessage("You don't have enough space in your inventory for this trade.");
					oldTarget.getPackets().sendGameMessage("Other player doesn't have enough space in their inventory for this trade.");
				}
			}
		}
	}

}
