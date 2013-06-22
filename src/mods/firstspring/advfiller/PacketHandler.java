package mods.firstspring.advfiller;

import mods.firstspring.advfiller.lib.BuildCraftProxy;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.Packet250CustomPayload;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeDirection;
import buildcraft.api.core.LaserKind;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;

import cpw.mods.fml.common.network.IPacketHandler;
import cpw.mods.fml.common.network.Player;

public class PacketHandler implements IPacketHandler
{
	@Override
	public void onPacketData(INetworkManager network, Packet250CustomPayload packet, Player player)
	{
		// Stringを==で比較しちゃいけないよ、俺は迂闊だった
		if (packet.channel.equals("advfiller_client"))
		{
			TileAdvFiller.receivedClientPacket(packet);
			return;
		}
		if (packet.channel.equals("advfiller_server"))
		{			
			TileAdvFiller.recevedServerPacket(packet, player);
			return;
		}
	}
}