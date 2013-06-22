/**
 * This is not original source
 * Reduced unused method and changes double to int
 * by FirstSpring@Polarstar
 */

/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */

package mods.firstspring.advfiller;

import net.minecraftforge.common.ForgeDirection;

public class Position
{
	private final int x, y, z;
	private final ForgeDirection forwardDirection;

	public Position(int ci, int cj, int ck)
	{
		x = ci;
		y = cj;
		z = ck;
		forwardDirection = ForgeDirection.UNKNOWN;
	}

	public Position(int ci, int cj, int ck, ForgeDirection corientation)
	{
		if(corientation == null)
			corientation = ForgeDirection.UNKNOWN;
		
		switch(corientation)
		{
		case NORTH:
		case SOUTH:
		case WEST:
		case EAST:
		case UNKNOWN:
			// valid argument.
			break;
		default:
			throw new IllegalArgumentException("corientation");
		}

		x = ci;
		y = cj;
		z = ck;
		forwardDirection = corientation;
	}

	public int getX()
	{
		return this.x;
	}

	public int getY()
	{
		return this.y;
	}

	public int getZ()
	{
		return this.z;
	}

	public Position moveRight(int step)
	{
		if(forwardDirection == ForgeDirection.UNKNOWN)
			throw new IllegalStateException("Cannot move unknown direction.");

		// Y軸で+90度回転させると右側（getRotationの引数は回転軸と回転の向きの指定です）
		ForgeDirection rightDirection =
			forwardDirection.getRotation(ForgeDirection.UP);

		return new Position(x + rightDirection.offsetX * step,
							y + rightDirection.offsetY * step,
							z + rightDirection.offsetZ * step,
							forwardDirection);
	}

	public Position moveLeft(int step)
	{
		return moveRight(-step);
	}

	public Position moveForwards(int step)
	{
		if(forwardDirection == ForgeDirection.UNKNOWN)
			throw new IllegalStateException("Cannot move unknown direction.");

		return new Position(x + forwardDirection.offsetX * step,
							y + forwardDirection.offsetY * step,
							z + forwardDirection.offsetZ * step,
							forwardDirection);
	}

	public Position moveBackwards(int step)
	{
		return moveForwards(-step);
	}

	public Position moveUp(int step)
	{
		if(forwardDirection == ForgeDirection.UNKNOWN)
			throw new IllegalStateException("Cannot move unknown direction.");

		int y2 = y + step;

		// world border limit.
		if (y2 <= 0)
			y2 = 1;

		if (y2 > 255)
			y2 = 255;

		return new Position(x, y2, z, forwardDirection);
	}

	public Position moveDown(int step)
	{
		return moveUp(-step);
	}

	@Override
	public String toString()
	{
		return "{" + x + ", " + y + ", " + z + "}";
	}

	public Position min(Position p)
	{
		return new Position(Math.min(p.x, x), Math.min(p.y, y), Math.min(p.z, z));
	}

	public Position max(Position p)
	{
		return new Position(Math.max(p.x, x), Math.max(p.y, y), Math.max(p.z, z));
	}

	@Override
	public boolean equals(Object o)
	{
		if (o instanceof Position)
		{
			Position p = (Position) o;
			return p.x == x && p.y == y && p.z == z;
		}
		return false;
	}

	// hashCodeが一致する場合はequalsは一致してもしなくてもOK
	// equalsで一致する場合は hashCode も「必ず」一致する必要があるぽよ。
	// Hash系コレクションで使わないならまず大丈夫だけど。
	// thanks to alalwww
	@Override
	public int hashCode()
	{
		return x * y * z;
	}
}
