package mods.firstspring.advfiller;

import ic2.api.Direction;
import ic2.api.energy.event.EnergyTileLoadEvent;
import ic2.api.energy.event.EnergyTileUnloadEvent;
import ic2.api.energy.tile.IEnergySink;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import mods.firstspring.advfiller.lib.BlockLib;
import mods.firstspring.advfiller.lib.BuildCraftProxy;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.Packet250CustomPayload;
import net.minecraft.network.packet.Packet3Chat;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraftforge.common.FakePlayerFactory;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.common.ForgeDirection;
import net.minecraftforge.common.MinecraftForge;
import buildcraft.api.core.IAreaProvider;
import buildcraft.api.power.IPowerProvider;
import buildcraft.api.power.IPowerReceptor;
import buildcraft.api.power.PowerFramework;
import buildcraft.api.transport.IPipeEntry;
import buildcraft.api.transport.IPipedItem;

import com.google.common.collect.Sets;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.network.Player;

public class TileAdvFiller extends TileEntity implements IPowerReceptor, IEnergySink, IPipeEntry
{
	private Thread initializeThread;
	private IPowerProvider powerProvider;
	private int dim;
	private boolean bcLoaded = BuildCraftProxy.loaded;
	private Ticket chunkTicket;
	// used on chunkloading message
	private EntityPlayer player;
	private boolean doRender = false;
	private GUIAreaProvider area;
	private int type;// 0 : Quarry Mode 1 : Remove Mode 2 : Filling Mode 3 : Flatten
				// Mode 4 : Exclusive Remove Mode 5 : TofuBuild Mode
	private int tick = 0;
	private int rate;
	private boolean initialized = false, disabled = false, finished = false;
	private boolean loopMode = false;
	private boolean doLoop = false;
	private boolean removeModeDrop = false;
	private boolean removeModeIteration = false;// false:descend true:ascend
	private boolean ic2EnergyNet = false;
	private List<Position> removeList;
	private ListIterator removeListIterator;
	private List<Position> fillList;
	private ListIterator fillListIterator;
	// Quarry Mode
	private boolean frameCreated = false;
	private List<Position> frameBuildList;
	private ListIterator frameBuildListIterator;
	private List<Position> quarryList;
	private ListIterator quarryListIterator;
	// used Quarry Mode and Flatten Mode
	private HashSet<Coord> ignoreCoordSet;
	private ForgeDirection orient;

	public TileAdvFiller()
	{
		rate = AdvFiller.energyRate;
		powerProvider = PowerFramework.currentFramework.createPowerProvider();
		powerProvider.configure(20, 1, 1000, 25, 1000);
		powerProvider.configurePowerPerdition(0, 100);
		// configure for quarry mode
		
		area = new GUIAreaProvider(0, 0, 0, null, 5, 5, 0, 0, 10);
		type = 0;
	}

	public void placed()
	{
		if (worldObj.isRemote)
			return;

		for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS)
		{
			TileEntity tile = worldObj.getBlockTileEntity(xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ);
			if (! (tile instanceof IAreaProvider))
				continue;
			
			IAreaProvider a = (IAreaProvider) tile;
			if (! calculateMarker(a))
				continue;
			
			if (bcLoaded)
				BuildCraftProxy.removeMarker(a);
			
			break;
		}
	}

	public boolean calculateMarker(IAreaProvider src)
	{
		GUIAreaProvider dest = GUIAreaProvider.fromAreaProvider(src, xCoord, yCoord, zCoord, orient);
		if(dest == null)
			return false;
		
		this.area = dest;
		return true;
	}

	public void preInit()
	{
		setDisable();
		dim = worldObj.getWorldInfo().getDimension();
		orient = ForgeDirection.values()[worldObj.getBlockMetadata(xCoord, yCoord, zCoord)].getOpposite();
		if (orient == ForgeDirection.UP || orient == ForgeDirection.DOWN || orient == ForgeDirection.UNKNOWN)
			return;
		
		if (bcLoaded)
		{
			buildcraft.core.Box box = BuildCraftProxy.proxy.getBox(this);
			box.reset();
			box.initialize(area);
		}

		initializeThread = new Thread(new AdvFillerInitializeThread(this));
		initializeThread.start();
		MinecraftForge.EVENT_BUS.post(new EnergyTileLoadEvent(this));
		ic2EnergyNet = true;
		initialized = true;
	}

	public void init()
	{
		this.tick = 0;
		switch (type)
		{
		case 0:
			frameCreated = false;
			ignoreCoordSet = new HashSet();
			calculateFrame();
			createQuarryList();
			break;
		case 1:
			createRemoveList();
			break;
		case 2:
			createFillList();
			break;
		case 3:
			createFlattenList();
			break;
		case 4:
			createExclusiveRemoveList();
			break;
		case 5:
			createTofuBuildList();
		}
		if (doLoop)
			return;
		// フロントの表示の更新に使用
		// 鯖側で実行するとブロックの情報(IDやらメタデータやら)と、
		// TileEntityの情報(getDescriptionPacketで作ったパケット)が送られる模様
		// 蔵側で実行すると表示の更新が行われる(結構重いので注意)
		worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
	}

	public void setDisable()
	{
		this.disabled = true;
	}

	public void setEnable()
	{
		this.disabled = false;
	}

	// パケットで使用
	public void setArea(GUIAreaProvider area)
	{
		this.area = area;
	}
	
	public void setArea(int left, int right, int up, int down, int forward)
	{
		this.area = this.area.rangeTo(left, right, up, down, forward);
	}
	
	public void setType(int type)
	{
		this.type = type;
	}

	@Override
	public void setPowerProvider(IPowerProvider provider)
	{
		this.powerProvider = provider;
	}

	@Override
	public IPowerProvider getPowerProvider()
	{
		return this.powerProvider;
	}

	@Override
	public void doWork()
	{
	}

	public void processEnergy()
	{
		if(AdvFiller.rsEnergy || !(BuildCraftProxy.loaded || Loader.isModLoaded("IC2")))
			if (worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord))
				this.powerProvider.receiveEnergy(Float.MAX_VALUE, ForgeDirection.UP);
	}

	@Override
	public void updateEntity()
	{
		super.updateEntity();
				
		if (worldObj.isRemote && (!AdvFiller.bcFrameRenderer || !bcLoaded))
		{
			// 描画用エンティティがスポーンしてない場合はスポーンさせる
			if (!this.doRender)
			{
				// 描画フラグと共有
				this.doRender = true;
				EntityRendererFiller e = new EntityRendererFiller(worldObj, this);
				// 天候エフェクトのふり
				worldObj.addWeatherEffect(e);
			}
			return;
		}
		if (worldObj.isRemote)
			return;
		processEnergy();
		if (!initialized)
			preInit();
		if (disabled)
			return;
		this.tick++;
		if (loopMode && this.tick > AdvFiller.loopTick)
		{
			this.doLoop = true;
			setDisable();
			player = null;
			initializeThread = new Thread(new AdvFillerInitializeThread(this));
			initializeThread.start();
			return;
		}
		if (finished)
			return;
		if (type == 0)
			doQuarryMode();
		if (type == 1 || type == 4)
			doRemoveMode();
		if (type == 2 || type == 5)
			doFillMode();
		if (type == 3)
			doFlattenMode();
	}

	// Quarry Mode

	public void doQuarryMode()
	{
		if (!frameCreated)
		{
			buildFrame();
			return;
		}
		dig();
	}

	public boolean checkFrame(int x, int y, int z)
	{
		if (y == area.yMin() || y == area.yMax())
			return (x == area.xMin() || x == area.xMax()) || (z == area.zMin() || z == area.zMax());
		else
			return (x == area.xMin() || x == area.xMax()) && (z == area.zMin() || z == area.zMax());
	}

	void calculateFrame()
	{
		frameBuildList = new ArrayList();
		removeList = new ArrayList();
				
		area = area.quarryFrame();
		
		if (area.yMax() > 255)
		{
			finished = true;
			return;
		}
		if (area.xMax() - area.xMin() < 2 || area.zMax() - area.zMin() < 2)
		{
			finished = true;
			return;
		}
		if (bcLoaded)
			BuildCraftProxy.proxy.getBox(this).initialize(area);
		for (int y = area.yMin(); y <= area.yMax(); y++)
		{
			for (int x = area.xMin(); x <= area.xMax(); x++)
			{
				for (int z = area.zMin(); z <= area.zMax(); z++)
				{
					if (checkFrame(x, y, z))
					{
						if (worldObj.getBlockId(x, y, z) != BuildCraftProxy.getFrameBlockId())
						{
							if (!AdvFiller.fillingSet.contains(worldObj.getBlockId(x, y, z)))
								removeList.add(new Position(x, y, z));
							frameBuildList.add(new Position(x, y, z));
						}
					} else
					{
						if (!AdvFiller.fillingSet.contains(worldObj.getBlockId(x, y, z)))
							removeList.add(new Position(x, y, z));
					}
				}
			}
		}
		frameBuildListIterator = frameBuildList.listIterator();
		removeListIterator = removeList.listIterator();
	}

	public void buildFrame()
	{
		if (powerProvider.useEnergy(rate, rate, false) != rate)
			return;
		powerProvider.useEnergy(rate, rate, true);
		if (removeListIterator.hasNext())
		{
			Position pos = (Position) removeListIterator.next();
			worldObj.setBlock(pos.getX(), pos.getY(), pos.getZ(), 0);
			return;
		}
		if (frameBuildListIterator.hasNext())
		{
			Position pos = (Position) frameBuildListIterator.next();
			worldObj.setBlock(pos.getX(), pos.getY(), pos.getZ(), BuildCraftProxy.getFrameBlockId(), 0, 3);
			return;
		}
		calculateFrame();
		if (removeListIterator.hasNext() || frameBuildListIterator.hasNext())
			return;
		frameCreated = true;
	}

	public void createQuarryList()
	{
		quarryList = new ArrayList();
		for (int y = yCoord - 1; y >= 1; y--)
		{
			for (int x = area.xMin() + 1; x <= area.xMax() - 1; x++)
			{
				for (int z = area.zMin() + 1; z <= area.zMax() - 1; z++)
				{
					if (checkBreakable(x, y, z))
						if (!AdvFiller.fillingSet.contains(worldObj.getBlockId(x, y, z)))
							quarryList.add(new Position(x, y, z));
				}
			}
		}
		quarryListIterator = quarryList.listIterator();
	}

	public boolean checkBreakable(int x, int y, int z)
	{
		if (ignoreCoordSet.contains(new Coord(x, z)))
			return false;
		if (!BlockLib.canChangeBlock(worldObj, x, y, z))
		{
			ignoreCoordSet.add(new Coord(x, z));
			return false;
		}
		return true;
	}

	public void dig()
	{

		if (powerProvider.useEnergy(rate * 4, rate * 4, false) != rate * 4)
			return;
		powerProvider.useEnergy(rate * 4, rate * 4, true);
		if (quarryListIterator.hasNext())
		{
			Position pos = (Position) quarryListIterator.next();
			List<ItemStack> stacks = BlockLib.getBlockDropped(worldObj, pos.getX(), pos.getY(), pos.getZ());
			if (AdvFiller.breakEffect)
				// クァーリーよりコピペ
				worldObj.playAuxSFXAtEntity(null, 2001, pos.getX(), pos.getY(), pos.getZ(), (worldObj.getBlockId(pos.getX(), pos.getY(), pos.getZ()) + (worldObj.getBlockMetadata(pos.getX(), pos.getY(), pos.getZ()) << 12)));
			worldObj.setBlock(pos.getX(), pos.getY(), pos.getZ(), 0);
			if (stacks == null || stacks.isEmpty())
				return;
			for (ItemStack stack : stacks)
			{
				if (bcLoaded)
				{
					ItemStack added = BuildCraftProxy.addToRandomInventory(stack, worldObj, xCoord, yCoord, zCoord, ForgeDirection.UNKNOWN);
					stack.stackSize -= added.stackSize;
				} else 
				stack = BlockLib.insertStackToNearInventory(stack, this);
				if (stack == null || stack.stackSize <= 0)
				{
					continue;
				}
				BuildCraftProxy.addToRandomPipeEntry(this, ForgeDirection.UNKNOWN, stack);
			}
		} else
		{
			finished = true;
			worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
		}
	}

	// RemoveMode

	public void createRemoveList()
	{
		removeList = new ArrayList();
		for (int y = area.yMin(); y <= area.yMax(); y++)
			for (int x = area.xMin(); x <= area.xMax(); x++)
				for (int z = area.zMin(); z <= area.zMax(); z++)
					if (BlockLib.canChangeBlock(worldObj, x, y, z) && worldObj.getBlockId(x, y, z) != 0)
						removeList.add(new Position(x, y, z));
		if (this.removeModeIteration)
			removeListIterator = removeList.listIterator();
		else
			removeListIterator = removeList.listIterator(removeList.size());
	}

	public void doRemoveMode()
	{
		Position pos;
		if (this.removeModeIteration)
		{
			for (int i = 0; i < 4; i++)
			{
				if (removeListIterator.hasNext())
				{
					if (type == 1)
					{
						if (powerProvider.useEnergy(rate, rate, false) != rate)
							return;
					}
					if (type == 4)
					{
						if (powerProvider.useEnergy(rate * 6, rate * 6, false) != rate * 6)
							return;
					}
					if (type == 1)
						powerProvider.useEnergy(rate, rate, true);
					if (type == 4)
					{
						powerProvider.useEnergy(rate * 6, rate * 6, true);
						i = 3;
					}
					pos = (Position) removeListIterator.next();
					doRemove(pos.getX(), pos.getY(), pos.getZ());

				} else if (!loopMode)
				{
					finished = true;
					worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
				}
			}
			return;
		} else
		{
			for (int i = 0; i < 4; i++)
			{
				if (removeListIterator.hasPrevious())
				{
					if (type == 1)
					{
						if (powerProvider.useEnergy(rate, rate, false) != rate)
							return;
					}
					if (type == 4)
					{
						if (powerProvider.useEnergy(rate * 6, rate * 6, false) != rate * 6)
							return;
					}
					if (type == 1)
						powerProvider.useEnergy(rate, rate, true);
					if (type == 4)
					{
						powerProvider.useEnergy(rate * 6, rate * 6, true);
						i = 3;
					}
					pos = (Position) removeListIterator.previous();
					doRemove(pos.getX(), pos.getY(), pos.getZ());
				} else if (!loopMode)
				{
					finished = true;
					worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
				}
			}
			return;
		}
	}

	public void doRemove(int x, int y, int z)
	{
		if (AdvFiller.breakEffect)
			// クァーリーよりコピペ
			worldObj.playAuxSFXAtEntity(null, 2001, x, y, z, (worldObj.getBlockId(x, y, z) + (worldObj.getBlockMetadata(x, y, z) << 12)));
		if (!(CommonProxy.proxy.isServer() && !AdvFiller.removeModeDrop) && removeModeDrop)
		{
			int meta = worldObj.getBlockMetadata(x, y, z);
			int id = worldObj.getBlockId(x, y, z);
			if (Block.blocksList[id] == null)
				return;
			Block.blocksList[id].dropBlockAsItem(worldObj, x, y, z, meta, 0);
		}
		worldObj.setBlock(x, y, z, 0);
	}

	// FillingMode
	public void createFillList()
	{
		fillList = new ArrayList();
		for (int y = area.yMin(); y <= area.yMax(); y++)
			for (int x = area.xMin(); x <= area.xMax(); x++)
				for (int z = area.zMin(); z <= area.zMax(); z++)
					if (AdvFiller.fillingSet.contains(worldObj.getBlockId(x, y, z)))
						fillList.add(new Position(x, y, z));
		fillListIterator = fillList.listIterator();
	}

	public void doFillMode()
	{
		Position pos;
		for (int i = 0; i < 4; i++)
		{
			if (fillListIterator.hasNext())
			{
				if (powerProvider.useEnergy(rate, rate, false) != rate)
					return;
				pos = (Position) fillListIterator.next();
				if (doFill(pos.getX(), pos.getY(), pos.getZ()))
					powerProvider.useEnergy(rate, rate, true);
				else if (!loopMode)
				{
					fillListIterator.previous();
					return;
				}
			} else if (!loopMode)
			{
				finished = true;
				worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
			}
		}
	}

	public boolean doFill(int x, int y, int z)
	{
		TileEntity tile = worldObj.getBlockTileEntity(xCoord, yCoord + 1, zCoord);
		if (!(tile instanceof IInventory))
			return false;
		IInventory inv = (IInventory) tile;
		ItemStack is = null;
		int stackslot = 0;
		for (int i = 0; i < inv.getSizeInventory(); i++)
		{
			is = inv.getStackInSlot(i);
			stackslot = i;
			if (is != null)
				break;
		}
		if (is == null)
			return false;
		boolean success = is.getItem().onItemUse(is, FakePlayerFactory.getMinecraft(worldObj), worldObj, x, y - 1, z, 1, 0.0f, 0.0f, 0.0f);
		if (is.stackSize < 1)
			inv.setInventorySlotContents(stackslot, null);
		return success;
	}

	ItemStack getStackFromUpperInventory()
	{
		TileEntity tile = worldObj.getBlockTileEntity(xCoord, yCoord + 1, zCoord);
		if (!(tile instanceof IInventory))
			return null;
		IInventory inv = (IInventory) tile;
		ItemStack is = null;
		for (int i = 0; i < inv.getSizeInventory(); i++)
		{
			is = inv.getStackInSlot(i);
			if (is != null)
				break;
		}
		return is;
	}

	// FlattenMode

	public void createFlattenList()
	{
		long time = System.currentTimeMillis();
		removeList = new ArrayList();
		fillList = new ArrayList();
		for (int y = yCoord; y <= 255; y++)
			for (int x = area.xMin(); x <= area.xMax(); x++)
				for (int z = area.zMin(); z <= area.xMax(); z++)
				{
					int blockID = worldObj.getBlockId(x, y, z);
					if (BlockLib.canChangeBlock(blockID, worldObj, x, y, z) && blockID != 0)
						removeList.add(new Position(x, y, z));
				}
		System.out.println(System.currentTimeMillis() - time);
		removeListIterator = removeList.listIterator();
		ignoreCoordSet = new HashSet();
		for (int y = yCoord - 1; y > 0; y--)
			for (int x = area.xMin(); x <= area.xMax(); x++)
				for (int z = area.zMin(); z <= area.zMax(); z++)
					if (true)
					{
						if (AdvFiller.fillingSet.contains(worldObj.getBlockId(x, y, z)))
							fillList.add(new Position(x, y, z));
						else
							ignoreCoordSet.add(new Coord(x, z));
					}
		fillListIterator = fillList.listIterator(fillList.size());
		System.out.println(System.currentTimeMillis() - time);
	}

	public boolean isIgnoreCoord(int x, int z)
	{
		if (ignoreCoordSet.contains(new Coord(x, z)))
			return true;
		return false;
	}

	public void doFlattenMode()
	{
		Position pos;
		for (int i = 0; i < 4; i++)
		{
			if (removeListIterator.hasNext())
			{
				if (powerProvider.useEnergy(rate, rate, false) != rate)
					return;
				powerProvider.useEnergy(rate, rate, true);
				pos = (Position) removeListIterator.next();
				doRemove(pos.getX(), pos.getY(), pos.getZ());
				if (i == 3)// 下に制御が行かないように
					return;
			}
		}
		for (int i = 0; i < 4; i++)
		{
			if (fillListIterator.hasPrevious())
			{
				if (powerProvider.useEnergy(rate, rate, false) != rate)
					return;
				pos = (Position) fillListIterator.previous();
				if (doFill(pos.getX(), pos.getY(), pos.getZ()))
					powerProvider.useEnergy(rate, rate, true);
				else
				{
					if (!loopMode)
					{
						fillListIterator.next();
						return;
					}
				}
			} else if (!loopMode)
			{
				finished = true;
				worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
			}
		}

	}

	// Exclusive Remove Mode

	public void createExclusiveRemoveList()
	{
		removeList = new ArrayList();
		int removeID = worldObj.getBlockId(xCoord, yCoord + 1, zCoord);
		if (removeID == 0)
		{
			finished = true;
			return;
		}
		for (int y = area.yMin(); y <= area.yMax(); y++)
			for (int x = area.xMin(); x <= area.xMax(); x++)
				for (int z = area.zMin(); z <= area.zMax(); z++)
					if (worldObj.getBlockId(x, y, z) == removeID)
						removeList.add(new Position(x, y, z));
		if (this.removeModeIteration)
			removeListIterator = removeList.listIterator();
		else
			removeListIterator = removeList.listIterator(removeList.size());
	}

	// TofuBuild Mode

	public void createTofuBuildList()
	{
		fillList = new ArrayList();
		for (int y = area.yMin(); y <= area.yMax(); y++)
		{
			if (y == area.yMin() || y == area.yMax())
			{
				for (int x = area.xMin(); x <= area.xMax(); x++)
				{
					for (int z = area.zMin(); z <= area.zMax(); z++)
					{
						if (worldObj.getBlockId(x, y, z) == 0)
							fillList.add(new Position(x, y, z));
					}
				}
			} else
				for (int x = area.xMin(); x <= area.xMax(); x++)
					for (int z = area.zMin(); z <= area.zMax(); z++)
					{
						if (((x == area.xMin() || x == area.xMax()) || (z == area.zMin() || z == area.zMax())) && worldObj.getBlockId(x, y, z) == 0)
							fillList.add(new Position(x, y, z));
					}
		}
		fillListIterator = fillList.listIterator();
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt)
	{
		super.readFromNBT(nbt);
		
		this.area = this.area.rangeTo(nbt.getInteger("left"),
									  nbt.getInteger("right"),
									  nbt.getInteger("up"),
									  nbt.getInteger("down"),
									  nbt.getInteger("forward"));
		
		type = nbt.getInteger("type");
		loopMode = nbt.getBoolean("loop");
		removeModeIteration = nbt.getBoolean("iterate");
		removeModeDrop = nbt.getBoolean("drop");
		PowerFramework.currentFramework.loadPowerProvider(this, nbt);
		powerProvider.configure(20, 1, 1000, 25, 1000);
		powerProvider.configurePowerPerdition(0, 100);

	}

	@Override
	public void writeToNBT(NBTTagCompound nbt)
	{
		super.writeToNBT(nbt);
		nbt.setInteger("left", area.getLeft());
		nbt.setInteger("right", area.getRight());
		nbt.setInteger("up", area.getUp());
		nbt.setInteger("down", area.getDown());
		nbt.setInteger("forward", area.getForward());
		nbt.setInteger("type", type);
		nbt.setBoolean("loop", loopMode);
		nbt.setBoolean("iterate", removeModeIteration);
		nbt.setBoolean("drop", removeModeDrop);
		PowerFramework.currentFramework.savePowerProvider(this, nbt);
	}

	public boolean isActive()
	{
		return !this.disabled && !finished;
	}

	@Override
	public Packet getDescriptionPacket()
	{
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(bos);

		try
		{
			dos.writeInt(xCoord);
			dos.writeInt(yCoord);
			dos.writeInt(zCoord);
			dos.writeInt(orient.ordinal());
			dos.writeInt(area.getLeft());
			dos.writeInt(area.getRight());
			dos.writeInt(area.getUp());
			dos.writeInt(area.getDown());
			dos.writeInt(area.getForward());
			dos.writeInt(type);
			dos.writeBoolean(loopMode);
			dos.writeBoolean(finished);
			dos.writeBoolean(disabled);
			dos.writeBoolean(removeModeIteration);
			dos.writeBoolean(removeModeDrop);
		} catch (Exception e)
		{
			e.printStackTrace();
		}

		Packet250CustomPayload packet = new Packet250CustomPayload();
		packet.channel = "advfiller_client";
		packet.data = bos.toByteArray();
		packet.length = bos.size();
		packet.isChunkDataPacket = true;
		return packet;
	}

	// クライアント用
	@Override
	public void invalidate()
	{
		if (!worldObj.isRemote)
		{
			// 止めても問題ないはず
			if (initializeThread != null)
				initializeThread.stop();
		}
		// クライアント用
		if (bcLoaded)
			BuildCraftProxy.proxy.getBox(this).deleteLasers();
		doRender = false;
		MinecraftForge.EVENT_BUS.post(new EnergyTileUnloadEvent(this));
		ic2EnergyNet = false;
		super.invalidate();
	}

	// クァーリーよりコピペ
	public void setLoadingChunks()
	{
		chunkTicket.getModData().setInteger("xCoord", xCoord);
		chunkTicket.getModData().setInteger("yCoord", yCoord);
		chunkTicket.getModData().setInteger("zCoord", zCoord);
		Set<ChunkCoordIntPair> chunks = Sets.newHashSet();
		ChunkCoordIntPair myChunk = new ChunkCoordIntPair(xCoord >> 4, zCoord >> 4);
		chunks.add(myChunk);
		ForgeChunkManager.forceChunk(this.chunkTicket, myChunk);
		for (int chunkX = area.xMin() >> 4; chunkX <= area.xMax() >> 4; chunkX++)
		{
			for (int chunkZ = area.zMin() >> 4; chunkZ <= area.zMax() >> 4; chunkZ++)
			{
				ChunkCoordIntPair chunk = new ChunkCoordIntPair(chunkX, chunkZ);
				ForgeChunkManager.forceChunk(this.chunkTicket, chunk);
				chunks.add(chunk);
			}
		}
		if (player != null)
		{
			PacketDispatcher.sendPacketToPlayer(new Packet3Chat(String.format("[ADVFILLER] The advfiller at %d %d %d will keep %d chunks loaded", xCoord, yCoord, zCoord, chunks.size())), (Player) player);
		}
	}

	@Override
	public int powerRequest(ForgeDirection from)
	{
		if (isActive())
			return (int) Math.ceil(Math.min(getPowerProvider().getMaxEnergyReceived(), getPowerProvider().getMaxEnergyStored() - getPowerProvider().getEnergyStored()));
		else
			return 0;
	}

	// IC2

	@Override
	public boolean acceptsEnergyFrom(TileEntity emitter, Direction direction)
	{
		return true;
	}

	@Override
	public boolean isAddedToEnergyNet()
	{
		//ネットワークに接続されているかを返す
		return ic2EnergyNet;
	}

	@Override
	public int demandsEnergy()
	{
		return (int) ((powerProvider.getMaxEnergyStored() - powerProvider.getEnergyStored()) * 2.5);
	}

	@Override
	public int injectEnergy(Direction directionFrom, int amount)
	{
		int requireEU = (int) ((powerProvider.getMaxEnergyStored() - powerProvider.getEnergyStored()) * 2.5);
		int injectMJ = (int) (Math.min(amount, requireEU) / 2.5);
		powerProvider.receiveEnergy(injectMJ, ForgeDirection.UP);
		return amount - (int) (injectMJ * 2.5);
	}

	@Override
	public int getMaxSafeInput()
	{
		return Integer.MAX_VALUE;
	}

	// IPipeEntry

	@Override
	public void entityEntering(ItemStack payload, ForgeDirection orientation)
	{
	}

	@Override
	public void entityEntering(IPipedItem item, ForgeDirection orientation)
	{
	}

	@Override
	public boolean acceptItems()
	{
		return false;
	}
	
	public boolean getBcLoaded()
	{
		return this.bcLoaded;
	}
	
	public Ticket getChunkTicket()
	{
		return this.chunkTicket;
	}
	
	public int getDim()
	{
		return this.dim;
	}
	
	public boolean isDoLoop()
	{
		return this.doLoop;
	}

	public Thread getInitializeThread() 
	{
		return this.initializeThread;
	}

	public int getType() 
	{
		return this.type;
	}

	public boolean isLoopMode() 
	{
		return this.loopMode;
	}

	public boolean isRemoveModeIteration() 
	{
		return this.removeModeIteration;
	}

	public boolean isRemoveModeDrop() 
	{
		return this.removeModeDrop;
	}
	
	public boolean isDoRender()
	{
		return this.doRender;
	}

	public void setChunkTicket(Ticket requestTicket) 
	{
		this.chunkTicket = requestTicket;
	}

	public void setFinished(boolean finished2) 
	{
		this.finished = finished2;
	}

	public void setDisabled(boolean disabled2) 
	{
		this.disabled = disabled2;
	}

	public void setDoLoop(boolean b) 
	{
		this.doLoop = b;
	}

	public void setInitializeThread(Thread thread) {
		this.initializeThread = thread;		
	}

	public void setLoopMode(boolean loop) {
		this.loopMode = loop;
	}

	public void setOrient(ForgeDirection orientation) {
		this.orient = orientation;		
	}

	public void setPlayer(EntityPlayer entityliving) {
		this.player = entityliving;		
	}

	public void setRemoveModeDrop(boolean drop) {
		this.removeModeDrop = drop;		
	}

	public void setRemoveModeIteration(boolean iterate) {
		this.removeModeIteration = iterate;
	}
	
	public GUIAreaProvider getArea()
	{
		return this.area;
	}
}
