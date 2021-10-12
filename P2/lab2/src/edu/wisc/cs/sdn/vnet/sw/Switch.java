package edu.wisc.cs.sdn.vnet.sw;

import net.floodlightcontroller.packet.Ethernet;
import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;
import java.util.*;

/**
 * @author Aaron Gember-Jacobson
 */
public class Switch extends Device
{
	//Data Structure to store MAC addresses
	//Object list is a 1D array with 2 values: 1. time of entry, 2. port
	HashMap<Object, List<Object>> addressTable;


	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Switch(String host, DumpFile logfile)
	{
		super(host,logfile);
		this.addressTable = new HashMap<Object, List<Object>>();
	}

	/**
	 * Handle an Ethernet packet received on a specific interface.
	 * @param etherPacket the Ethernet packet that was received
	 * @param inIface the interface on which the packet was received
	 */
	public void handlePacket(Ethernet etherPacket, Iface inIface)
	{
		System.out.println("*** -> Received packet: " +
                etherPacket.toString().replace("\n", "\n\t"));
		
		//handle packets

		//add source address to table
		this.addressTable.put(etherPacket.getSourceMAC(), new Object[]{inIface, System.currentTimeMillis()});
		//look up dest address
		Object[] destEntry = this.addressTable.get(etherPacket.getDestinationMAC());
		//send if valid
		if (destEntry != null || System.currentTimeMillis() - destEntry[0] <= 15000){
			sendPacket(etherPacket, destEntry[1]);
		}
		else{ // not valid --> broadcast
			// send packet to each interface that isn't the source
			for(Iface iface: this.interfaces.values()){
				if(!iface.equals(inIface)){sendPacket(etherPacket, iface);}
			}
		}

	}
}
