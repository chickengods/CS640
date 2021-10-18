package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device
{	
	/** Routing table for the router */
	private RouteTable routeTable;
	
	/** ARP cache for the router */
	private ArpCache arpCache;
	
	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Router(String host, DumpFile logfile)
	{
		super(host,logfile);
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache();
	}
	
	/**
	 * @return routing table for the router
	 */
	public RouteTable getRouteTable()
	{ return this.routeTable; }
	
	/**
	 * Load a new routing table from a file.
	 * @param routeTableFile the name of the file containing the routing table
	 */
	public void loadRouteTable(String routeTableFile)
	{
		if (!routeTable.load(routeTableFile, this))
		{
			System.err.println("Error setting up routing table from file "
					+ routeTableFile);
			System.exit(1);
		}
		
		System.out.println("Loaded static route table");
		System.out.println("-------------------------------------------------");
		System.out.print(this.routeTable.toString());
		System.out.println("-------------------------------------------------");
	}
	
	/**
	 * Load a new ARP cache from a file.
	 * @param arpCacheFile the name of the file containing the ARP cache
	 */
	public void loadArpCache(String arpCacheFile)
	{
		if (!arpCache.load(arpCacheFile))
		{
			System.err.println("Error setting up ARP cache from file "
					+ arpCacheFile);
			System.exit(1);
		}
		
		System.out.println("Loaded static ARP cache");
		System.out.println("----------------------------------");
		System.out.print(this.arpCache.toString());
		System.out.println("----------------------------------");
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
		
		//check if the packets should be sent
		boolean valid = true;

		//check for IPv4
		if (valid) {
			if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4) {
				valid = false;
			}
		}
		//get payload
		IPv4 head = (IPv4) etherPacket.getPayload();
		//check checksum
		if (valid) {
			int checksum = head.getChecksum();
			head.resetChecksum();
			byte[] head_data = head.serialize();
			head.deserialize(head_data, 0, head_data.length);
			int checksum2 = head.getChecksum();
			if (checksum != checksum2) {
				valid = false;
			}
		}

		//check TTL
		if (valid) {
			if (head.getTtl() == 0) {
				valid = false;
			} else {
				head.setTtl((byte) (head.getTtl() - 1));
				if (head.getTtl() == 0) {
					valid = false;
				}
			}
		}

		//check for correct dest interface
		if (valid) {
			head.resetChecksum();
			if (head.getDestinationAddress() == inIface.getIpAddress()) {
				valid = false;
			}
		}
		//check the rest interfaces on router
		if (valid) {
			for (Iface iface : interfaces.values()) {
				if (head.getDestinationAddress() == iface.getIpAddress()) {
					valid = false;
				}
			}
		}

		//if the packets are valid send them off
		if (valid){
			//get table entry
			RouteEntry entry = routeTable.lookup(head.getDestinationAddress());

			//check for a valid entry
			if (entry != null && entry.getInterface() != inIface){
				//find  the next ip
				int nextIP = entry.getGatewayAddress();
				if (nextIP == 0){ // next ip is dest
					nextIP = head.getDestinationAddress();
				}
				ArpEntry nextIPArp = arpCache.lookup(nextIP);

				if (nextIPArp != null) {
					//prep packet to be sent
					etherPacket.setSourceMACAddress(entry.getInterface().getMacAddress().toBytes());
					etherPacket.setDestinationMACAddress(nextIPArp.getMac().toBytes());

					//send packets
					boolean sent = sendPacket(etherPacket, entry.getInterface());

					//check if it was sent
					if (sent == false){
						System.out.println("Something went wrong, packet wasn't sent");
					}
				}
			}

		}

	}
}
