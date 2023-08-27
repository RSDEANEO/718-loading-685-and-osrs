package com.rs.game.npc.cox.impl;

import com.rs.game.Entity;
import com.rs.game.Hit;
import com.rs.game.WorldTile;
import com.rs.game.npc.combat.CombatScript;
import com.rs.game.npc.combat.NPCCombatDefinitions;
import com.rs.game.npc.cox.COXBoss;
import com.rs.game.player.Player;
import com.rs.game.player.content.raids.cox.ChambersOfXeric;
import com.rs.game.player.content.raids.cox.chamber.Chamber;
import com.rs.game.tasks.WorldTask;

import static com.rs.game.tasks.WorldTasksManager.schedule;

/**
 * @author Simplex
 * @since Nov 18, 2020
 */
public class GreatOlmRightClaw extends COXBoss {
    public GreatOlmRightClaw(ChambersOfXeric raid, int id, WorldTile tile, Chamber chamber) {
        super(raid, id, tile, chamber);
        addFreezeDelay(Integer.MAX_VALUE);
        // hands don't attack
        setCustomCombatScript(CombatScript.DO_NOTHING);
    }

    @Override
    public boolean preAttackCheck(Player attacker) {
        if(getRaid().getGreatOlmChamber().getOlm().rightHandDown()) {
            return false;
        }

        return super.preAttackCheck(attacker);
    }

    @Override
    public void handleIngoingHit(Hit hit) {
        if(hit.getLook() != Hit.HitLook.MAGIC_DAMAGE && hit.getLook() != Hit.HitLook.HEALED_DAMAGE) {
            if(hit.getSource().isPlayer()) {
                hit.getSource().asPlayer().sendMessage("The claw resists your non-magic attack!");
            }
            hit.setDamage(0);
        }

        if(hit.getDamage() != 0) {
            raid.getGreatOlmChamber().getOlm().incomingDamage(hit, false);
        }
        super.handleIngoingHit(hit);
    }

    @Override
    public void sendDeath(Entity killer) {
        GreatOlmRightClaw claw = this;
        final NPCCombatDefinitions defs = getCombatDefinitions();
        resetWalkSteps();
        getCombat().removeTarget();
        setNextAnimation(null);
        schedule(new WorldTask() {
            int loop;

            @Override
            public void run() {
                if (loop == 0) {
                    raid.getGreatOlmChamber().getOlm().clawDeathStart(claw);
                } else if (loop >= 2) {
                    //reset();
                    //finish();
                    stop();
                    raid.getGreatOlmChamber().getOlm().clawDeathEnd(claw);
                }
                loop++;
            }
        }, 0, 1);
    }

    public boolean restoringClaw() {
        return getRaid().getGreatOlmChamber().getOlm().getRestoreClaw() == this;
    }

    @Override
    public void processNPC() {
        if(restoringClaw()) {
            int restoreStep = getMaxHitpoints() / 60;
            //forceTalk("Restoring .. " + getHitpoints() + " / " + getMaxHitpoints() + " increment=" +restoreStep);
            this.setHitpoints(getHitpoints() + restoreStep);
            if(getHitpoints() >= getMaxHitpoints()) {
                setHitpoints(getMaxHitpoints());
            }
            applyHit(this, 0);
            return;
        }
        super.processNPC();
    }
    @Override
    public int getHitbarSprite(Player player) {
        return !restoringClaw() ? super.getHitbarSprite(player) : 22464;
    }

}
