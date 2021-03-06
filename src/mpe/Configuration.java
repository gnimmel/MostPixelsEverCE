package mpe;

import java.net.UnknownHostException;

// XML parser library includes
import xmlcomponents.Jocument;
import xmlcomponents.Jode;

import processing.core.PApplet;
import processing.core.PConstants;

/**
 * An object that encapsulates the configuration context of the display system.
 * @author Brandt Westing TACC
 *
 */
public class Configuration {

	private PApplet applet_;
	
	private String server_ = null;;
	private int port_;
	private int[] localDim_;
	private int[] masterDim_;
	private int[] offsets_;
	private int[] tileRes_;
	private int[] numTiles_;
	private int[] bezels_;
	private String display_;
	private int rank_;
	private boolean debug_ = false;
	
	// are we the leader process?
	boolean isLeader_ = false;
	
	// the number of follower processes (total num processes - 1)
	int numFollowers_;
	
	// this constructer is in case you forget the file location or just omit it
	public Configuration(PApplet p)
	{
		this("configuration.xml", p);
	}

	public Configuration(String _file, PApplet _p)
	{
		// the processing applet that we are taking care of!
		applet_ = _p;
		
		tileRes_   = new int[2];
		numTiles_  = new int[2];
		bezels_    = new int[2];
		localDim_  = new int[2];
		masterDim_ = new int[2];
		offsets_   = new int[2];
		
		// set up the pipeline for reading XML
		
		Jode root = null;
		root = Jocument.load(_file);
		
		System.out.println(root.name());
						
		// my DISPLAY identifier
		display_ = System.getenv("DISPLAY");
		
		if(System.getenv("RANK") != null)
			rank_ = Integer.valueOf(System.getenv("RANK"));
		else rank_ = -1;
		
		// get my hostname to identify me in the config file
		String hostname = "";
		try {
			hostname = java.net.InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			System.out.println("I can't determine my hostname!");
		}
		
		Jode config = root.single("configuration");
		Jode dimensions = config.single("dimensions");
		tileRes_[0]   = Integer.parseInt(dimensions.attribute("screenWidth").v);
		tileRes_[1]   = Integer.parseInt(dimensions.attribute("screenHeight").v);
		numTiles_[0]  = Integer.parseInt(dimensions.attribute("numTilesWidth").v);
		numTiles_[1]  = Integer.parseInt(dimensions.attribute("numTilesHeight").v);
		bezels_[0]    = Integer.parseInt(dimensions.attribute("mullionWidth").v);
		bezels_[1]    = Integer.parseInt(dimensions.attribute("mullionHeight").v);
		debug_ = Integer.parseInt(dimensions.attribute("debug").v) == 1;
		
		numFollowers_ = config.children().getLength() - 2;
		
		Jode head = config.first("head");
		
		if(head != null)
		{
			server_ = head.attribute("host").v;
			port_ = Integer.parseInt(head.attribute("port").v);
			System.out.println("Server: "+ server_ + ":" + Integer.toString(port_));
		}
		else
		{
			System.out.println("Couldn't get head! Setting default.");
			server_ = "localhost";
		}
		
		Jode child = null;
		
		// our rank is defined in the environment, so we should search for that
		if(rank_ != -1)
		{
			
			// Find out if this process is the head process
			for(int i = 0; i < config.children().getLength(); i++)
			{
				child = config.children().get(i);
				String nodeName = child.n;
				
				// found the head child
				if(nodeName.equals("head"))
				{
					// we are the head node
					if(Integer.parseInt(child.attribute("rank").v) == rank_)
					{
						Jode headChild = child.first(); 
						localDim_[0] = Integer.parseInt(headChild.attribute("width").v);
						localDim_[1] = Integer.parseInt(headChild.attribute("height").v);
						
						masterDim_[0] = localDim_[0];
						masterDim_[1] = localDim_[1];
						
						// offsets
						offsets_[0] = 0;
						offsets_[1] = 0;
						isLeader_ = true;
						return;
					}
				}
			}
			
			// find the entry for the correct host
			for(int i = 1; i < config.children().getLength(); i++)
			{
				child = config.children().get(i);
				
				if(Integer.parseInt(child.attribute("rank").v) == rank_)
					break; // we found our xml entry!
			}
		}
		
		// RANK env. variable was not set, resort to hostname lookup
		else
		{
			System.out.println("RANK was not found in the environment, using hostname lookup. Try exporting RANK for each process in the future!");
			// Find out if this process is the head process
			for(int i = 0; i < config.children().getLength(); i++)
			{
				child = config.children().get(i);
				String nodeName = child.n;
				
				// found the head child
				if(nodeName.equals("head"))
				{
					// we are the head node
					if(child.attribute("host").v.equals(hostname))
					{
						Jode headChild = child.first(); 
						localDim_[0] = Integer.parseInt(headChild.attribute("width").v);
						localDim_[1] = Integer.parseInt(headChild.attribute("width").v);
						
						masterDim_[0] = localDim_[0];
						masterDim_[1] = localDim_[1];
						
						// offsets
						offsets_[0] = 0;
						offsets_[1] = 0;
						isLeader_ = true;

						return;
					}
				}
			}
			
			// find the entry for the correct host
			for(int i = 1; i < config.children().getLength(); i++)
			{
				child = config.children().get(i);
				String host = child.attribute("host").v;
				String display = child.attribute("display").v;
				
				if(host != null)
				{
					if(host.equals(hostname) && display.equals(display_))
					{
						break;
					}
				}
			}
		}
		
		if(child == null)
		{
			System.out.println("ERROR: Couldn't find my entry in the configuration. Exiting.");
			System.exit(-1);
		}
		
		// child corresponds to entry with the correct hostname here
		Jode childi;
		childi = child.first();
		int mini = Integer.parseInt(childi.attribute("i").v);
		int maxi = Integer.parseInt(childi.attribute("i").v);
		int minj = Integer.parseInt(childi.attribute("j").v);
		int maxj = Integer.parseInt(childi.attribute("j").v);
		
		for(int i = 0; i < child.children().getLength(); i++)
		{
			childi = child.children().get(i);
			if(Integer.parseInt(childi.attribute("i").v) < mini)
				mini = Integer.parseInt(childi.attribute("i").v);
			if(Integer.parseInt(childi.attribute("i").v) > maxi)
				maxi = Integer.parseInt(childi.attribute("i").v);
			if(Integer.parseInt(childi.attribute("j").v) < minj)
				minj = Integer.parseInt(childi.attribute("j").v);
			if(Integer.parseInt(childi.attribute("j").v) > maxj)
				maxj = Integer.parseInt(childi.attribute("j").v);
		}
		
		// this get the size of the monitor array for this host in screens
		int rangei = maxi - mini + 1;
		int rangej = maxj - minj + 1;
		
		int totalMullionsX = (rangei - 1)*bezels_[0];
		int totalMullionsY = (rangej - 1)*bezels_[1];
		
		localDim_[0] = rangei*tileRes_[0] + totalMullionsX;
		localDim_[1] = rangej*tileRes_[1] + totalMullionsY;
		
		masterDim_[0] = numTiles_[0]*tileRes_[0] + (numTiles_[0] - 1)*bezels_[0];
		masterDim_[1] = numTiles_[1]*tileRes_[1] + (numTiles_[1] - 1)*bezels_[1];
		
		// offsets
		offsets_[0] = (mini)*tileRes_[0] + mini*bezels_[0];
		offsets_[1] = (minj)*tileRes_[1] + mini*bezels_[1];
		
		printSettings();
	}
	
	public PApplet getApplet()
	{
		return applet_;
	}
		
	public String getServer() {
		return server_;
	}

	public int getPort() {
		return port_;
	}

	public int[] getLocalDim() {
		return localDim_;
	}
	
	// special for processing
	public int getLWidth()
	{
		return localDim_[0];
	}
	
	// special for processing
	public int getLHeight()
	{
		return localDim_[1];
	}

	public int[] getMasterDim() {
		return masterDim_;
	}

	public int[] getOffsets() {
		return offsets_;
	}

	public int getNumFollowers()
	{
		return numFollowers_;
	}

	public boolean isLeader()
	{
		return isLeader_;
	}
	
	public boolean getDebug()
	{
		return debug_;
	}
	
	public int getRank()
	{
		return rank_;
	}
	
	public void printSettings()
	{
		System.out.println("Settings: Rank: " + rank_ + 
					", offsets: " + offsets_[0] + "," + offsets_[1] +
					", lDims: " + localDim_[0] + "," + localDim_[1] +
					", mDims: " + masterDim_[0] + "," + masterDim_[1]);
	}
	
}
