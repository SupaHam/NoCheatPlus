package fr.neatmonster.nocheatplus.checks.moving;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.actions.ParameterName;
import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.ViolationData;
import fr.neatmonster.nocheatplus.utilities.PlayerLocation;

/**
 * The MorePackets check (previously called Speedhack check) will try to identify players that send more than the usual
 * amount of move-packets to the server to be able to move faster than normal, without getting caught by the other
 * checks (flying/running).
 * 
 * It monitors the number of packets sent to the server within 1 second and compares it to the "legal" number of packets
 * for that timeframe (22).
 */
public class MorePackets extends Check {

    /**
     * The usual number of packets per timeframe.
     * 
     * 20 would be for perfect internet connections, 22 is good enough.
     */
    private final static int packetsPerTimeframe = 22;

    /**
     * Instantiates a new more packets check.
     */
    public MorePackets() {
        super(CheckType.MOVING_MOREPACKETS);
    }

    /**
     * Checks a player.
     * 
     * Players get assigned a certain amount of "free" packets as a limit initially. Every move packet reduces that
     * limit by 1. If more than 1 second of time passed, the limit gets increased by 22 * time in seconds, up to 50 and
     * they get a new "setback" location. If the player reaches limit = 0 -> teleport them back to "setback". If there was
     * a long pause (maybe lag), limit may be up to 100.
     * 
     * @param player
     *            the player
     * @param from
     *            the from
     * @param to
     *            the to
     * @return the location
     */
    public Location check(final Player player, final PlayerLocation from, final PlayerLocation to, final MovingData data, final MovingConfig cc) {
    	// Take time once, first:
    	final long time = System.currentTimeMillis();
    	

        Location newTo = null;

        if (!data.hasMorePacketsSetBack()){
        	// TODO: Check if other set-back is appropriate or if to set on other events.
        	if (data.hasSetBack()) {
        		data.setMorePacketsSetBack(data.getSetBack(to));
        	}
        	else {
        		data.setMorePacketsSetBack(from);
        	}
        }

        // Take a packet from the buffer.
        data.morePacketsBuffer--;

        // Player used up buffer, they fail the check.
        if (data.morePacketsBuffer < 0) {
        	
            // Increment violation level.
            data.morePacketsVL = -data.morePacketsBuffer;
            
            // Execute whatever actions are associated with this check and the violation level and find out if we should
            // cancel the event.
            final ViolationData vd = new ViolationData(this, player, data.morePacketsVL, -data.morePacketsBuffer, cc.morePacketsActions);
            if (cc.debug || vd.needsParameters()) {
            	vd.setParameter(ParameterName.PACKETS, Integer.toString(-data.morePacketsBuffer));
            }
            if (executeActions(vd)){
            	newTo = data.getMorePacketsSetBack(); 
            }
            
        }

        if (data.morePacketsLastTime + 1000 < time) {
            // More than 1 second elapsed, but how many?
            final double seconds = (time - data.morePacketsLastTime) / 1000D;

            // For each second, fill the buffer.
            data.morePacketsBuffer += packetsPerTimeframe * seconds;

            // If there was a long pause (maybe server lag?), allow buffer to grow up to 100.
            if (seconds > 2) {
                if (data.morePacketsBuffer > 100) {
                	data.morePacketsBuffer = 100;
                }
            } else if (data.morePacketsBuffer > 50) {
                // Only allow growth up to 50.
                data.morePacketsBuffer = 50;
            }
            // Set the new "last" time.
            data.morePacketsLastTime = time;

            // Set the new "setback" location.
            if (newTo == null) {
            	data.setMorePacketsSetBack(from);
            }
        } else if (data.morePacketsLastTime > time) {
            // Security check, maybe system time changed.
            data.morePacketsLastTime = time;
        }

        if (newTo == null) {
        	return null;
        }

        // Compose a new location based on coordinates of "newTo" and viewing direction of "event.getTo()" to allow the
        // player to look somewhere else despite getting pulled back by NoCheatPlus.
        return new Location(player.getWorld(), newTo.getX(), newTo.getY(), newTo.getZ(), to.getYaw(), to.getPitch());
    }
    
}
