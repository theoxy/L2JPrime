/*
 * l2jprime Project - sgprimehost.com 
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */
package com.l2jprime.gameserver.network.clientpackets;

import com.l2jprime.gameserver.model.actor.instance.L2PcInstance;

public final class RequestSkillList extends L2GameClientPacket
{
	@SuppressWarnings("unused")
	private int _unk1;
	
	@SuppressWarnings("unused")
	private int _unk2;
	
	@SuppressWarnings("unused")
	private int _unk3;
	
	@Override
	protected void readImpl()
	{
		// this is just a trigger packet. it has no content
	}
	
	@Override
	protected void runImpl()
	{
		final L2PcInstance cha = getClient().getActiveChar();
		
		if (cha == null)
			return;
		
		cha.sendSkillList();
	}
	
	@Override
	public String getType()
	{
		return "[C] 3F RequestSkillList";
	}
}
