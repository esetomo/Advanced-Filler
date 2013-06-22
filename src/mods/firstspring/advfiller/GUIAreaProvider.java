package mods.firstspring.advfiller;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.ForgeDirection;
import buildcraft.api.core.IAreaProvider;

public class GUIAreaProvider implements IAreaProvider 
{
	private final int x;
	private final int y;
	private final int z;
	private final ForgeDirection orient;

	private final int left;
	private final int right;
	private final int up;
	private final int down;
	private final int forward;
	
	private final int xMin;	
	private final int yMin;
	private final int zMin;
	private final int xMax;
	private final int yMax;
	private final int zMax;
	
	public GUIAreaProvider(int x, int y, int z, ForgeDirection orient, int left, int right, int up, int down, int forward)
	{
		if(orient == null || orient == ForgeDirection.UNKNOWN) // 方向未取得の場合はとりあえず北向きで計算
			orient = ForgeDirection.NORTH;
		
		this.x = x;
		this.y = y;
		this.z = z;
		this.orient = orient;
		this.left = left;
		this.right = right;
		this.up = up;
		this.down = down;
		this.forward = forward;

		// 基準点(ブロックの1マス前)
		Position basePos = new Position(x, y, z, orient).moveForwards(1);
		
		// 対角線上の２点
		Position pos1 = basePos.moveLeft(left).moveDown(down);
		Position pos2 = basePos.moveForwards(forward).moveRight(right).moveUp(up);
		
		this.xMin = Math.min(pos1.getX(), pos2.getX());
		this.yMin = Math.min(pos1.getY(), pos2.getY());
		this.zMin = Math.min(pos1.getZ(), pos2.getZ());
		
		this.xMax = Math.max(pos1.getX(), pos2.getX());
		this.yMax = Math.max(pos1.getY(), pos2.getY());
		this.zMax = Math.max(pos1.getZ(), pos2.getZ());
	}
	
	public static GUIAreaProvider fromAreaProvider(IAreaProvider a, int x, int y, int z, ForgeDirection orient)
	{
		if(orient == null || orient == ForgeDirection.UNKNOWN) // 方向未取得の場合はとりあえず北向きで計算
			orient = ForgeDirection.NORTH;
		
		Position pos = new Position(x, y, z, orient).moveForwards(1);

		final int minX = pos.getX() - Math.min(a.xMin(), a.xMax());
		final int maxX = Math.max(a.xMax(), a.xMin()) - pos.getX();
		final int minY = pos.getY() - Math.min(a.yMin(), a.yMax());
		final int maxY = Math.max(a.yMax(), a.yMin()) - pos.getY();
		final int minZ = pos.getZ() - Math.min(a.zMin(), a.zMax());
		final int maxZ = Math.max(a.zMin(), a.zMax()) - pos.getZ();
		
		for (int v : new int[]{minX, maxX, minY, maxY, minZ, maxZ})
		{
			if(v < 0 || v > AdvFiller.maxDistance)
				return null; // 設定値範囲外
		}
		
		final int left;
		final int right;
		final int forward;
		
		switch (orient)
		{
		case SOUTH:
			if(minZ != 0) // 手前側の面は基準点を含む必要あり
				return null;
			forward = maxZ;
			left = maxX;
			right = minX;
			break;
		case NORTH:
			if(maxZ != 0)
				return null;
			forward = minZ;
			left = minX;
			right = maxX;
			break;
		case EAST:
			if(minX != 0)
				return null;
			forward = maxX;
			left = minZ;
			right = maxZ;
			break;
		case WEST:
			if(maxX != 0)
				return null;
			forward = minX;
			left = maxZ;
			right = minZ;
			break;
		default:
			// 横向きじゃないので異常
			throw new IllegalStateException("Invalid orient");
		}
		
		return new GUIAreaProvider(x, y, z, orient, left, right, maxY, minY, forward);
	}
	
	public GUIAreaProvider moveTo(int x, int y, int z, ForgeDirection orient)
	{
		return new GUIAreaProvider(x, y, z, orient, this.left, this.right, this.up, this.down, this.forward);
	}

	public GUIAreaProvider rangeTo(int left, int right, int up, int down, int forward) {
		return new GUIAreaProvider(this.x, this.y, this.z, this.orient, left, right, up, down, forward);
	}
	
	public GUIAreaProvider quarryFrame()
	{
		return new GUIAreaProvider(this.x, this.y, this.z, this.orient, this.left, this.right, 4, 0, this.forward);
	}
	
	@Override
	public int xMin() {
		return xMin;
	}

	@Override
	public int yMin() {
		return yMin;
	}

	@Override
	public int zMin() {
		return zMin;
	}

	@Override
	public int xMax() {
		return xMax;
	}

	@Override
	public int yMax() {
		return yMax;
	}

	@Override
	public int zMax() {
		return zMax;
	}

	@Override
	public void removeFromWorld() {
		// GUIによる設定のため撤去すべき設置物はなし
	}
	
	public int getLeft()
	{
		return left;
	}
	
	public int getRight()
	{
		return right;
	}
	
	public int getUp()
	{
		return up;
	}
	
	public int getDown()
	{
		return down;
	}
	
	public int getForward()
	{
		return forward;
	}
}
