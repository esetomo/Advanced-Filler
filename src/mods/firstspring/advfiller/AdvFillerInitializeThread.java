package mods.firstspring.advfiller;

import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Type;

public class AdvFillerInitializeThread implements Runnable
{

	private TileAdvFiller tile;

	public AdvFillerInitializeThread(TileAdvFiller tile)
	{
		this.tile = tile;
	}

	@Override
	public void run()
	{
		tile.setFinished(false);
		if (!tile.isDoLoop())
		{
			ForgeChunkManager.releaseTicket(tile.getChunkTicket());
			tile.setChunkTicket(ForgeChunkManager.requestTicket(AdvFiller.instance, tile.worldObj, Type.NORMAL));
			tile.setLoadingChunks();
		}
		tile.init();
		tile.setEnable();
		tile.setDoLoop(false);
	}

}
