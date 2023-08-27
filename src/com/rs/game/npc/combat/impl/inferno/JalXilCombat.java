package com.rs.game.npc.combat.impl.inferno;

import com.rs.game.Animation;
import com.rs.game.Entity;
import com.rs.game.World;
import com.rs.game.npc.NPC;
import com.rs.game.npc.combat.CombatScript;
import com.rs.game.npc.combat.NPCCombatDefinitions;
import com.rs.utils.Utils;

public class JalXilCombat extends CombatScript {

	@Override
	public Object[] getKeys() {
		return new Object[]
		{ 27698 };
	}

	@Override
	public int attack(NPC npc, Entity target) {
		final NPCCombatDefinitions defs = npc.getCombatDefinitions();
		int distanceX = target.getX() - npc.getX();
		int distanceY = target.getY() - npc.getY();
		int size = npc.getSize();
		int hit = 0;
		int attackStyle = Utils.random(2);
		if (attackStyle == 0 && (distanceX > size || distanceX < -1 || distanceY > size || distanceY < -1)) {
			attackStyle = 1;
		}
		switch (attackStyle) {
		case 0:
			hit = getRandomMaxHit(npc, npc.getMaxHit() - 36, NPCCombatDefinitions.MELEE, target);
			npc.setNextAnimation(new Animation(defs.getAttackEmote()));
			delayHit(npc, 0, target, getMeleeHit(npc, hit));
			break;
		case 1:
			hit = getRandomMaxHit(npc, npc.getMaxHit(), NPCCombatDefinitions.RANGE, target);
			npc.setNextAnimation(new Animation(27605));
			World.sendProjectile(npc.transform(2, 1, 0), target, 6377, 74, 16, 30, 45, 16, 64);
			World.sendProjectile(npc.transform(0, 1, 0), target, 6377, 74, 16, 30, 45, 16, 64);
			delayHit(npc, 2, target, getRangeHit(npc, hit));
			break;
		}
		return defs.getAttackDelay();
	}
}
