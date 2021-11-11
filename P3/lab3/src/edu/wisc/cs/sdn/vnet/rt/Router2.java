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
  
    //if ARP handle AR{
    if (etherPacket.getEtherType() == Ethernet.TYPE_ARP){
      this.handlePacketARP(etherPacket, inIface);
    }
    else if (etherPacket.getEtherType() == Ethernet.TYPE_IPv4){ // if IP handle IP
      this.handlePacketIP(etherPacket, inIface);
    }
    // else do nothing
  }	

  public void handlePacketIP(Ethernet etherPacket, Iface inIface){
    
    //to keep track if we should stop

    //check to make sure it is a IP packet
    if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4){
      return;
    }

    IPv4 head = (IPv4) etherPacket.getPayload();

    //check checksum
			int checksum = head.getChecksum();
			head.resetChecksum();
			byte[] head_data = head.serialize();
			head.deserialize(head_data, 0, head_data.length);
			int checksum2 = head.getChecksum();
			if (checksum != checksum2) {
			 return;
			}

      //check TTL
      if (head.getTtl() == 0) {
        //TODO add code for ICMP TTL here
        return;
			} else {
				head.setTtl((byte) (head.getTtl() - 1));
				if (head.getTtl() == 0) {
					//TODO add code for ICM{ TTL here
          return;
				}
			}


      head.resetChecksum();

      //this is a part to double check things
      //TODO add deistino part un reachable
      //TODO add echo reply
      //TODO handlRIP
      if (head.getDestinationAddress() == inIface.getIpAddress()) {
        return;
      }
      for (Iface iface: interfaces.values()) {
        if (head.getDestinationAddress() == iface.getIpAddress()) {
          return;
        }
      }

      this.forwardPacket(etherPacjet, inIface);
  }

  public void forwardPacket(Ethernet etherPacket, Iface inIface){
  }

  public void handlePacketARP(Ethernet etherPacket, Iface inIface){
  }

  public void handlePacketRIP(Ethernet etherPacket, Iface inIface){
  }


  public void forwardRIP(Iface inIface, boolean req, boolean broad){
  }


  public void createRT(){
  
  }

  public void icmpError(Ethernet etherPacket, Iface inIface, int type, int code, boolean echo) {
		Ethernet ether = new Ethernet();
		IPv4 ip = new IPv4();
		ICMP icmp = new ICMP();
		Data data = new Data();
		ether.setPayload(ip);
		ip.setPayload(icmp);
		icmp.setPayload(data);

		// set IP fields
		ip.setTtl((byte)64);
		ip.setProtocol(Ipv4.PROTOCOL_ICMP);
		IPv4 srcPacket = (IPv4)etherPacket.getPayload();

		if (echo) {
			ip.setSourceAdress(srcPacket.getDestinationAddress());
		} else {
			ip.setSourceAdress(inIface.getIpAddress()); // not sure if right
		}

		ip.setDestinationAddress(srcPacket.getSourceAddress());

		//set ethernet fields
		ether.setEtherType(Ethernet.TYPE_IPv4);
		ether.setSourceMACAddress(inIface.getMacAddress().toBytes());

		// FIXME think this is right but keep in mind I don't check for null
		// find next hop
		RouteEntry entry = routeTable.lookup(srcPacket.getSourceAddress());
		int nextIP = entry.getGatewayAddress();
		if (nextIP == 0) { // next ip is dest
			nextIP = srcPacket.getSourceAddress();
		}
		ArpEntry nextIPArp = arpCache.lookup(nextIP);
		ether.setDestinationMACAddress(nextIPArp.getMac().toBytes());

		// set ICMP fields
		icmp.setIcmpType((byte)type);
		icmp.setIcmpCode((byte)code);

		// payload
		byte[] srcBytes = srcPacket.serialize();
		byte[] icmpPayload;
		int numBytes;

		if (echo) {
			numBytes = srcPacket.getTotalLength();
			icmpPayload = new byte[numBytes + 4]; // 4 bytes for padding
		} else {
			numBytes = srcPacket.getHeaderLength() * 4 + 8;
			icmpPayload = new byte[numBytes + 4]; // 4 bytes for padding
		}

		// copy source header to payload
		for (int i = 0; i < numBytes; i++) {
			icmpPayload[i + 4] = srcBytes[i];
		}

		data.setData(icmpPayload);
		sendPacket(ether, inIface);
}

