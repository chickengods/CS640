package edu.wisc.cs.sdn.vnet.sw;

import net.floodlightcontroller.packet.Ethernet;
import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;
import net.floodlightcontroller.packet.MACAddress;

import java.util.*;

/**
 * @author Aaron Gember-Jacobson
 */
public class Switch extends Device
{
	//Data Structure to store MAC addresses
	//sctruct class to store iface and time
	// I wantedto use a object array for this but I couldn't get the syntax
	// workin, but this is just as good if not better
	class TableEntry {
		Iface iface;
		long time;
		public  TableEntry(long time, Iface iface){
			this.iface = iface;
			this.time = time;
		}
	}
	//hashtable to store data
	HashMap<MACAddress, TableEntry> addressTable;


	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Switch(String host, DumpFile logfile)
	{
		super(host,logfile);
		this.addressTable = new HashMap<MACAddress, TableEntry>();
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
		this.addressTable.put(etherPacket.getSourceMAC(), new TableEntry(System.currentTimeMillis(), inIface) );
		//look up dest address
		TableEntry destEntry = this.addressTable.get(etherPacket.getDestinationMAC());
		//send if valid
		if (destEntry != null && System.currentTimeMillis() - destEntry.time <= 15000){
			sendPacket(etherPacket, destEntry.iface);
		}
		else{ // not valid --> broadcast
			// send packet to each interface that isn't the source
			for(Iface iface: this.interfaces.values()){
				if(!iface.equals(inIface)){sendPacket(etherPacket, iface);}
			}
		}

	}
}
