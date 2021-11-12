package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.ICMP;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.packet.RIPv2;
import net.floodlightcontroller.packet.RIPv2Entry;
import net.floodlightcontroller.packet.MACAddress;


import java.util.*;
import java.nio.*;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device {
	/** Routing table for the router */
	private RouteTable routeTable;

	/** ARP cache for the router */
	private ArpCache arpCache;

	// stores packet queues for ARP
	private HashMap<Integer, Queue> pq;
	// timer for RIP
	private  Timer timer;

	/**
	 * Creates a router for a specific host.
	 * 
	 * @param host hostname for the router
	 */
	public Router(String host, DumpFile logfile) {
		super(host, logfile);
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache();
		this.pq = new HashMap<Integer, Queue>();
	}

	/**
	 * @return routing table for the router
	 */
	public RouteTable getRouteTable() {
		return this.routeTable;
	}

	/**
	 * Load a new routing table from a file.
	 * 
	 * @param routeTableFile the name of the file containing the routing table
	 */
	public void loadRouteTable(String routeTableFile) {
		if (!routeTable.load(routeTableFile, this)) {
			System.err.println("Error setting up routing table from file " + routeTableFile);
			System.exit(1);
		}

		System.out.println("Loaded static route table");
		System.out.println("-------------------------------------------------");
		System.out.print(this.routeTable.toString());
		System.out.println("-------------------------------------------------");
	}

	/**
	 * Load a new ARP cache from a file.
	 * 
	 * @param arpCacheFile the name of the file containing the ARP cache
	 */
	public void loadArpCache(String arpCacheFile) {
		if (!arpCache.load(arpCacheFile)) {
			System.err.println("Error setting up ARP cache from file " + arpCacheFile);
			System.exit(1);
		}

		System.out.println("Loaded static ARP cache");
		System.out.println("----------------------------------");
		System.out.print(this.arpCache.toString());
		System.out.println("----------------------------------");
	}

	/**
	 * Handle an Ethernet packet received on a specific interface.
	 * 
	 * @param etherPacket the Ethernet packet that was received
	 * @param inIface     the interface on which the packet was received
	 */
	public void handlePacket(Ethernet etherPacket, Iface inIface) {
		System.out.println("*** -> Received packet: " + etherPacket.toString().replace("\n", "\n\t"));

		// if ARP handle AR{
		if (etherPacket.getEtherType() == Ethernet.TYPE_ARP) {
			this.handlePacketARP(etherPacket, inIface);
		} else if (etherPacket.getEtherType() == Ethernet.TYPE_IPv4) { // if IP handle IP
			this.handlePacketIP(etherPacket, inIface);
		}
		// else do nothing
	}

	public void handlePacketIP(Ethernet etherPacket, Iface inIface) {

		// to keep track if we should stop

		// check to make sure it is a IP packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4) {
			return;
		}

		IPv4 head = (IPv4) etherPacket.getPayload();

		// check checksum
		int checksum = head.getChecksum();
		head.resetChecksum();
		byte[] head_data = head.serialize();
		head.deserialize(head_data, 0, head_data.length);
		int checksum2 = head.getChecksum();
		if (checksum != checksum2) {
			return;
		}

		// check TTL
		if (head.getTtl() == 0) {
			icmpError(etherPacket, inIface, 11, 0, false);
			return;
		} else {
			head.setTtl((byte) (head.getTtl() - 1));
			if (head.getTtl() == 0) {
				icmpError(etherPacket, inIface, 11, 0, false);
				return;
			}
		}

		head.resetChecksum();


  if(head.getDestinationAddress() == IPv4.toIPv4Address("244.0.0.9")) {
    if(head.getProtocol() == IPv4.PROTOCOL_UDP){
      UDP udp_temp = (UDP)head.getPayload();
      if(udp_temp.getDestinationPort() == UDP.RIP_PORT){
        handlePacketRIP(etherPacket, inIface);
        return;
      }
    } 
  }



		// this is a part to double check things
		if (head.getDestinationAddress() == inIface.getIpAddress()) {
			// ICMP destination port unreachable
			if (head.getProtocol() == IPv4.PROTOCOL_UDP) {
				// Handle RIP
				UDP udp = (UDP) head.getPayload();
				if  (udp.getDestinationPort() == UDP.RIP_PORT) {
					handlePacketRIP(etherPacket, inIface);
				}
        else {
					icmpError(etherPacket, inIface, 3, 3, false);
				}
			} 
      else if (head.getProtocol() == IPv4.PROTOCOL_TCP) {
				icmpError(etherPacket, inIface, 3, 3, false);
				// Echo reply
			} 
      else if (head.getProtocol() == IPv4.PROTOCOL_ICMP) {
				ICMP icmp = (ICMP) head.getPayload();
				if (icmp.getIcmpType() == 8) {
					icmpError(etherPacket, inIface, 0, 0, true);
				}
			}
			return;
		}

		for (Iface iface : interfaces.values()) {
			if (head.getDestinationAddress() == iface.getIpAddress()) {
			  // ICMP destination port unreachable
			  if (head.getProtocol() == IPv4.PROTOCOL_UDP) {
				// Handle RIP
				UDP udp = (UDP) head.getPayload();
				if (udp.getDestinationPort() == UDP.RIP_PORT) {
					handlePacketRIP(etherPacket, inIface);
				}
        else {
					icmpError(etherPacket, inIface, 3, 3, false);
				}
			} 
      else if (head.getProtocol() == IPv4.PROTOCOL_TCP) {
				icmpError(etherPacket, inIface, 3, 3, false);
				// Echo reply
			} 
      else if (head.getProtocol() == IPv4.PROTOCOL_ICMP) {
				ICMP icmp = (ICMP) head.getPayload();
				if (icmp.getIcmpType() == 8) {
					icmpError(etherPacket, inIface, 0, 0, true);
				}
      }   
        return;	
      }
		}
		
    System.out.println("past RIP part");
		RouteEntry entry = this.routeTable.lookup(head.getDestinationAddress());

		// ICMP destination net unreachable
		if (entry == null) {
			icmpError(etherPacket, inIface, 3, 0, false);
			return;
		}

		if (entry.getInterface() == inIface) {
			return;
		}

		int nextIP = entry.getGatewayAddress();
		if (nextIP == 0) { // next ip is dest
			nextIP = head.getDestinationAddress();
		}
		ArpEntry nextIPArp = arpCache.lookup(nextIP);

		// TODO
		if (nextIPArp == null) {
      System.out.println("ARP ip handle part");
			ARP a = new ARP();
			a.setProtocolType(ARP.PROTO_TYPE_IP);
			a.setProtocolAddressLength((byte) 4);
			a.setOpCode(ARP.OP_REQUEST);

			a.setHardwareType(ARP.HW_TYPE_ETHERNET);
			a.setHardwareAddressLength((byte) Ethernet.DATALAYER_ADDRESS_LENGTH);
			a.setTargetHardwareAddress(ByteBuffer.allocate(8).putInt(0).array());
			a.setTargetProtocolAddress(nextIP);
			a.setSenderHardwareAddress(inIface.getMacAddress().toBytes());
			a.setSenderProtocolAddress(inIface.getIpAddress());

			Ethernet e = new Ethernet();

			e.setEtherType(Ethernet.TYPE_ARP);
			e.setSourceMACAddress(inIface.getMacAddress().toBytes());
			e.setPayload(a);
			e.setDestinationMACAddress("FF:FF:FF:FF:FF:FF");
			e.serialize();

			if (!pq.containsKey(nextIP)) {
				pq.put(nextIP, new LinkedList());
			}

			Queue q = pq.get(nextIP);
			q.add(etherPacket);

      final int nextIP_final = nextIP;
      final Ethernet e_final = e;


			Thread reply = new Thread(new Runnable() {
				public void run() {

					try {

						sendPacket(e, inIface);
						Thread.sleep(1000);
						if (arpCache.lookup(nextIP_final) != null) {
							return;
						}

						sendPacket(e, inIface);
						Thread.sleep(1000);
						if (arpCache.lookup(nextIP_final) != null) {
							return;
						}

						sendPacket(e, inIface);
						Thread.sleep(1000);
						if (arpCache.lookup(nextIP_final) != null) {
							return;
						}
            System.out.println(" ARP reach failed");
						// Destination host unreachable message
						icmpError(etherPacket, inIface, 3, 1, false);
					} catch (Exception e) {
						System.out.println(e);
						System.out.println("Something went wrong with ARP reply");
					}
				}
			});
			reply.start();
		} else {
      System.out.println(" reg ip send part");
			// prep packet to be sent
			etherPacket.setSourceMACAddress(entry.getInterface().getMacAddress().toBytes());
			etherPacket.setDestinationMACAddress(nextIPArp.getMac().toBytes());

			// send packets
			boolean sent = sendPacket(etherPacket, entry.getInterface());

			// check if it was sent
			if (sent == false) {
				System.out.println("Something went wrong, packet wasn't sent");
			}
		}

	}

	public void handlePacketARP(Ethernet etherPacket, Iface inIface) {

    System.out.println("hanndle ARP");

		ARP head = (ARP) etherPacket.getPayload();

		if (head.getOpCode() != ARP.OP_REQUEST) {
			if (head.getOpCode() != ARP.OP_REPLY) {
				return;
			} else {
				ByteBuffer bb = ByteBuffer.wrap(head.getSenderProtocolAddress());
				int addy = bb.getInt();
				arpCache.insert(new MACAddress(head.getSenderHardwareAddress()), addy);
				Queue sends = pq.get(new Integer(addy));
				while (sends != null && sends.peek() != null) {
					Ethernet temp_p = (Ethernet) sends.poll();
					temp_p.setDestinationMACAddress(head.getSenderHardwareAddress());
					sendPacket(temp_p, inIface);
				}
			}
		}

		int targ = ByteBuffer.wrap(head.getTargetProtocolAddress()).getInt();
		if (inIface.getIpAddress() != targ) {
			return;
		}

		Ethernet e = new Ethernet();

		e.setDestinationMACAddress(etherPacket.getSourceMACAddress());
		e.setSourceMACAddress(inIface.getMacAddress().toBytes());
		e.setEtherType(Ethernet.TYPE_ARP);

		ARP a = new ARP();

		a.setProtocolType(ARP.PROTO_TYPE_IP);
		a.setProtocolAddressLength((byte) 4);
		a.setOpCode(ARP.OP_REPLY);

		a.setHardwareAddressLength((byte) Ethernet.DATALAYER_ADDRESS_LENGTH);
		a.setHardwareType(ARP.HW_TYPE_ETHERNET);
		a.setTargetHardwareAddress(head.getSenderHardwareAddress());
		a.setTargetProtocolAddress(head.getSenderProtocolAddress());
		a.setSenderHardwareAddress(inIface.getMacAddress().toBytes());
		a.setSenderProtocolAddress(inIface.getIpAddress());

		e.setPayload(a);
		e.serialize();
		sendPacket(e, inIface);
	}

	public void handlePacketRIP(Ethernet etherPacket, Iface inIface) {
		IPv4 head = (IPv4) etherPacket.getPayload();

		UDP data = (UDP) head.getPayload();
		int checksum = data.getChecksum();
		head.resetChecksum();
		byte[] seri = data.serialize();
		data.deserialize(seri, 0, seri.length);
		int checksum2 = data.getChecksum();

		if (checksum != checksum2) {
			return;
		}

    RIPv2 rip = (RIPv2) data.getPayload();
    if (rip.getCommand() == RIPv2.COMMAND_REQUEST){
          this.forwardRIP(inIface, false, true);
          return;
    }

    for (RIPv2Entry r : rip.getEntries()) {
      int cost = r.getMetric() + 1;
      int next = r.getNextHopAddress();
      int mask = r.getSubnetMask();
      int addy = r.getAddress();

      r.setMetric(cost);
      
      RouteEntry entry = this.routeTable.lookup(addy);

      if (null == entry || entry.getCost() > cost){
        if (null != entry){
          this.routeTable.update(addy, next,mask, inIface, cost);
        }
        else{
          this.routeTable.insert(addy, next, mask, inIface, cost);
        }
        for (Iface iface : this.interfaces.values()){
          this.forwardRIP(iface, false, false);
        }
      }
    }
	}

	public void forwardRIP(Iface inIface, boolean req, boolean broad) {

	  Ethernet e = new Ethernet();
    IPv4 ip = new IPv4();
    UDP udp = new UDP();
    RIPv2 rip = new RIPv2();

    e.setPayload(ip);
    ip.setPayload(udp);
    udp.setPayload(rip);


    e.setEtherType(Ethernet.TYPE_IPv4);
    e.setSourceMACAddress("FF:FF:FF:FF:FF:FF");
    
    if (broad){
      e.setDestinationMACAddress("FF:FF:FF:FF:FF:FF");
    }
    else {
      e.setDestinationMACAddress(inIface.getMacAddress().toBytes());
    }

    ip.setTtl((byte)64);
    ip.setVersion((byte)4);
    ip.setProtocol(IPv4.PROTOCOL_UDP);
    if (broad){
      ip.setDestinationAddress("244.0.0.9");
    }
    else{
      ip.setDestinationAddress(inIface.getIpAddress());
    }





    udp.setDestinationPort(UDP.RIP_PORT);
    udp.setSourcePort(UDP.RIP_PORT);

    if (req){
      rip.setCommand(RIPv2.COMMAND_REQUEST);
    }
    else{
      rip.setCommand(RIPv2.COMMAND_RESPONSE);
    }

    for (RouteEntry entry : this.routeTable.getEntries()){
      int cost = entry.getCost();
      int mask = entry.getMaskAddress();
      int next = inIface.getIpAddress();
      int addy = entry.getDestinationAddress();

      RIPv2Entry r_entry = new RIPv2Entry(addy, mask, cost);
      r_entry.setNextHopAddress(next);
      rip.addEntry(r_entry);

    }
    e.serialize();
    sendPacket(e, inIface);
  }

	public void timerRun() {
		for (Iface iface : this.interfaces.values()) {
      this.forwardRIP(iface, false, true);
		}
	}

	class updateTimer extends TimerTask {
		public void run() {
			timerRun();
		}
	}

	public void createRouteTable() {
		for (Iface iface : this.interfaces.values()) {
			int mask = iface.getSubnetMask();
			int dest = iface.getIpAddress() & mask;
			this.routeTable.insert(dest, 0, mask, iface, 1);
		}

		for (Iface ifaces : this.interfaces.values()) {
			this.forwardRIP(ifaces, true, true);
		}
		this.timer = new Timer();
		this.timer.scheduleAtFixedRate(new updateTimer(), 10000, 10000);

	}




	private void icmpError(Ethernet etherPacket, Iface inIface, int type, int code, boolean echo){
		Ethernet ether = new Ethernet();
		IPv4 ip = new IPv4();
		ICMP icmp = new ICMP();
		Data data = new Data();
		ether.setPayload(ip);
		ip.setPayload(icmp);
		icmp.setPayload(data);	

		ether.setEtherType(Ethernet.TYPE_IPv4);
		IPv4 IpPacket = (IPv4)(etherPacket.getPayload());

		int payLoadLen = (int)(((IPv4)(etherPacket.getPayload())).getTotalLength());
		byte original[] = IpPacket.serialize();	
		byte dataBytes[] = new byte[4 + (echo ? payLoadLen : IpPacket.getHeaderLength() * 4 + 8)];

		//System.out.println("echo: "+echo+ " lens: "+original.length+" | "+dataBytes.length);
	
		for( int i = 0; i < (echo ? payLoadLen : (IpPacket.getHeaderLength() * 4 + 8)); i++)
			dataBytes[i + 4] = original[i];
		data.setData(dataBytes);
		
		byte d = 64;
		ip.setTtl(d);
		ip.setProtocol(IPv4.PROTOCOL_ICMP);
		//ip.setSourceAddress(inIface.getIpAddress());
		ip.setDestinationAddress(((IPv4)(etherPacket.getPayload())).getSourceAddress());

		icmp.setIcmpType((byte)type);
		icmp.setIcmpCode((byte)code);
	
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();
		int dstAddr = ipPacket.getSourceAddress();
		RouteEntry bestMatch = this.routeTable.lookup(dstAddr);
		if (null == bestMatch)
		{ return; }
		// Make sure we don't sent a packet back out the interface it came in
        	Iface outIface = bestMatch.getInterface();
        	//if (outIface == inIface)
        	//{ return; }
		ip.setSourceAddress(echo ? ipPacket.getDestinationAddress() : outIface.getIpAddress());

        	// Set source MAC address in Ethernet header
        	ether.setSourceMACAddress(inIface.getMacAddress().toBytes());
		
        	// If no gateway, then nextHop is IP destination
        	int nextHop = bestMatch.getGatewayAddress();
        	if (0 == nextHop)
        	{ nextHop = dstAddr; }

        	// Set destination MAC address in Ethernet header
        	ArpEntry arpEntry = this.arpCache.lookup(nextHop);
        	if (null == arpEntry)
        	{ return; }
        	ether.setDestinationMACAddress(arpEntry.getMac().toBytes());

		System.out.println("sent packet:" + ether);
        	this.sendPacket(ether, outIface);
	}




	public void icmpError2(Ethernet etherPacket, Iface inIface, int type, int code, boolean echo) {
		Ethernet ether = new Ethernet();
		IPv4 ip = new IPv4();
		ICMP icmp = new ICMP();
		Data data = new Data();
		ether.setPayload(ip);
		ip.setPayload(icmp);
		icmp.setPayload(data);

		// set IP fields
		ip.setTtl((byte) 64);
		ip.setProtocol(IPv4.PROTOCOL_ICMP);
		IPv4 srcPacket = (IPv4) etherPacket.getPayload();

		if (echo) {
			ip.setSourceAddress(srcPacket.getDestinationAddress());
		} else {
			ip.setSourceAddress(inIface.getIpAddress()); // not sure if right
		}

		ip.setDestinationAddress(srcPacket.getSourceAddress());

		// set ethernet fields
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
		icmp.setIcmpType((byte) type);
		icmp.setIcmpCode((byte) code);

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
}
