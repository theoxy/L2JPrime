/* l2jprime Project - sgprimehost.com 
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
package com.l2jprime.gameserver.network;

import java.nio.ByteBuffer;

import org.apache.log4j.Logger;

import com.l2jprime.Config;
import com.l2jprime.gameserver.Shutdown;
import com.l2jprime.gameserver.managers.PacketsLoggerManager;
import com.l2jprime.gameserver.network.L2GameClient.GameClientState;
import com.l2jprime.gameserver.network.clientpackets.*;
import com.l2jprime.gameserver.network.serverpackets.ActionFailed;
import com.l2jprime.logs.Log;
import com.l2jprime.netcore.IClientFactory;
import com.l2jprime.netcore.IMMOExecutor;
import com.l2jprime.netcore.IPacketHandler;
import com.l2jprime.netcore.MMOConnection;
import com.l2jprime.netcore.NetcoreConfig;
import com.l2jprime.netcore.ReceivablePacket;
import com.l2jprime.util.PacketsFloodProtector;
import com.l2jprime.util.Util;

/**
 * Stateful Packet Handler<BR>
 * The Stateful approach prevents the server from handling inconsistent packets, examples:<BR>
 * <li>Clients sends a MoveToLocation packet without having a character attached. (Potential errors handling the packet).</li> <li>Clients sends a RequestAuthLogin being already authed. (Potential exploit).</li> <BR>
 * <BR>
 * Note: If for a given exception a packet needs to be handled on more then one state, then it should be added to all these states.
 * @author l2jprime
 */

public final class L2GamePacketHandler implements IPacketHandler<L2GameClient>, IClientFactory<L2GameClient>, IMMOExecutor<L2GameClient>
{
	private static final Logger LOGGER = Logger.getLogger(L2GamePacketHandler.class);
	
	// implementation
	@Override
	public ReceivablePacket<L2GameClient> handlePacket(final ByteBuffer buf, final L2GameClient client)
	{
		
		if (client.dropPacket())
		{
			if (Config.DEBUG_PACKETS)
				Log.add("Packet Dropped", "GamePacketsLog");
			client.sendPacket(ActionFailed.STATIC_PACKET);
			return null;
		}
		
		final int opcode = buf.get() & 0xFF;
		int opcode2 = -1;
		
		if (opcode == 0xd0)
		{
			
			if (buf.remaining() >= 2)
			{
				opcode2 = buf.getShort() & 0xffff;
			}
			
		}
		
		if (client.getActiveChar() != null)
		{// already done EnterWorld
		
			final String character = client.getActiveChar().getName();
			String packet = "" + opcode;
			if (opcode2 != -1)
				packet = packet + "," + opcode2;
			
			// check if character has block on packet
			if (PacketsLoggerManager.getInstance().isCharacterPacketBlocked(character, packet))
			{
				client.sendPacket(ActionFailed.STATIC_PACKET);
				return null;
			}
			
			// Before Anything, check if character is Monitored or has Block on received Packet
			if (PacketsLoggerManager.getInstance().isCharacterMonitored(character))
			{
				PacketsLoggerManager.getInstance().logCharacterPacket(character, packet);
			}
			
		}
		
		if (!PacketsFloodProtector.tryPerformAction(opcode, opcode2, client))
		{
			client.sendPacket(ActionFailed.STATIC_PACKET);
			return null;
		}
		
		ReceivablePacket<L2GameClient> msg = null;
		GameClientState state = client.getState();
		
		if (Config.DEBUG_PACKETS)
		{
			Log.add("Packet: " + Integer.toHexString(opcode) + " on State: " + state.name() + " Client: " + client.toString(), "GamePacketsLog");
		}
		
		switch (state)
		{
			case CONNECTED:
				switch (opcode)
				{
					case 0x00:
						msg = new ProtocolVersion();
						break;
					case 0x08:
						msg = new AuthLogin();
						break;
					default:
						printDebug(opcode, buf, state, client);
						break;
				}
				break;
			case AUTHED:
				switch (opcode)
				{
					case 0x09:
						msg = new Logout();
						break;
					case 0x0b:
						msg = new CharacterCreate();
						break;
					case 0x0c:
						msg = new CharacterDelete();
						break;
					case 0x0d:
						msg = new CharacterSelected();
						break;
					case 0x0e:
						msg = new NewCharacter();
						break;
					case 0x62:
						msg = new CharacterRestore();
						break;
					case 0x68:
						msg = new RequestPledgeCrest();
						break;
					
					// single packet
					default:
						printDebug(opcode, buf, state, client);
						break;
				}
				break;
			case IN_GAME:
			{
				
				if (!NetcoreConfig.getInstance().LIST_ALLOWED_OFFLINE_OPCODES.contains(opcode))
				{
					
					if (client.getActiveChar() == null || client.getActiveChar().isOnline() == 0)
					{
						// if not in shutdown
						if (!Shutdown.getInstance().isShutdownStarted())
							LOGGER.warn("ATTENTION: Account " + client.accountName + " is trying to send packet with opcode " + opcode + " without enterning in the world (online status is FALSE)..");
						break;
					}
					
				}
				
				switch (opcode)
				{
					case 0x01:
						msg = new MoveBackwardToLocation();
						break;
					// case 0x02:
					// // Say ... not used any more ??
					// break;
					case 0x03:
						msg = new EnterWorld();
						break;
					case 0x04:
						msg = new Action();
						break;
					case 0x09:
						msg = new Logout();
						break;
					case 0x0a:
						msg = new AttackRequest();
						break;
					case 0x0f:
						msg = new RequestItemList();
						break;
					case 0x10:
						// // RequestEquipItem ... not used any more, instead "useItem"
						break;
					case 0x11:
						msg = new RequestUnEquipItem();
						break;
					case 0x12:
						msg = new RequestDropItem();
						break;
					case 0x14:
						msg = new UseItem();
						break;
					case 0x15:
						msg = new TradeRequest();
						break;
					case 0x16:
						msg = new AddTradeItem();
						break;
					case 0x17:
						msg = new TradeDone();
						break;
					case 0x1a:
						msg = new DummyPacket();
						break;
					case 0x1b:
						msg = new RequestSocialAction();
						break;
					case 0x1c:
						msg = new ChangeMoveType2();
						break;
					case 0x1d:
						msg = new ChangeWaitType2();
						break;
					case 0x1e:
						msg = new RequestSellItem();
						break;
					case 0x1f:
						msg = new RequestBuyItem();
						break;
					case 0x20:
						msg = new RequestLinkHtml();
						break;
					case 0x21:
						msg = new RequestBypassToServer();
						break;
					case 0x22:
						msg = new RequestBBSwrite();
						break;
					case 0x23:
						msg = new DummyPacket();
						break;
					case 0x24:
						msg = new RequestJoinPledge();
						break;
					case 0x25:
						msg = new RequestAnswerJoinPledge();
						break;
					case 0x26:
						msg = new RequestWithdrawalPledge();
						break;
					case 0x27:
						msg = new RequestOustPledgeMember();
						break;
					case 0x28:
						// // RequestDismissPledge
						break;
					case 0x29:
						msg = new RequestJoinParty();
						break;
					case 0x2a:
						msg = new RequestAnswerJoinParty();
						break;
					case 0x2b:
						msg = new RequestWithDrawalParty();
						break;
					case 0x2c:
						msg = new RequestOustPartyMember();
						break;
					case 0x2d:
						// RequestDismissParty
						break;
					case 0x2e:
						msg = new DummyPacket();
						break;
					case 0x2f:
						msg = new RequestMagicSkillUse();
						break;
					case 0x30:
						msg = new Appearing(); // (after death)
						break;
					case 0x31:
						if (Config.ALLOW_WAREHOUSE)
						{
							msg = new SendWareHouseDepositList();
						}
						break;
					case 0x32:
						msg = new SendWareHouseWithDrawList();
						break;
					case 0x33:
						msg = new RequestShortCutReg();
						break;
					case 0x34:
						msg = new DummyPacket();
						break;
					case 0x35:
						msg = new RequestShortCutDel();
						break;
					case 0x36:
						msg = new CannotMoveAnymore();
						break;
					case 0x37:
						msg = new RequestTargetCanceld();
						break;
					case 0x38:
						msg = new Say2();
						break;
					case 0x3c:
						msg = new RequestPledgeMemberList();
						break;
					case 0x3e:
						msg = new DummyPacket();
						break;
					case 0x3f:
						msg = new RequestSkillList();
						break;
					case 0x41:
						msg = new MoveWithDelta();
						// // MoveWithDelta ... unused ?? or only on ship ??
						break;
					case 0x42:
						msg = new RequestGetOnVehicle();
						break;
					case 0x43:
						msg = new RequestGetOffVehicle();
						break;
					case 0x44:
						msg = new AnswerTradeRequest();
						break;
					case 0x45:
						msg = new RequestActionUse();
						break;
					case 0x46:
						msg = new RequestRestart();
						break;
					case 0x47:
						msg = new RequestSiegeInfo();
						break;
					case 0x48:
						msg = new ValidatePosition();
						break;
					case 0x49:
						// // RequestSEKCustom
						break;
					// THESE ARE NOW TEMPORARY DISABLED
					case 0x4a:
						msg = new StartRotating();
						break;
					case 0x4b:
						msg = new FinishRotating();
						break;
					case 0x4d:
						msg = new RequestStartPledgeWar();
						break;
					case 0x4e:
						msg = new RequestReplyStartPledgeWar();
						break;
					case 0x4f:
						msg = new RequestStopPledgeWar();
						break;
					case 0x50:
						msg = new RequestReplyStopPledgeWar();
						break;
					case 0x51:
						msg = new RequestSurrenderPledgeWar();
						break;
					case 0x52:
						msg = new RequestReplySurrenderPledgeWar();
						break;
					case 0x53:
						msg = new RequestSetPledgeCrest();
						break;
					case 0x55:
						msg = new RequestGiveNickName();
						break;
					case 0x57:
						msg = new RequestShowBoard();
						break;
					case 0x58:
						msg = new RequestEnchantItem();
						break;
					case 0x59:
						msg = new RequestDestroyItem();
						break;
					case 0x5b:
						msg = new SendBypassBuildCmd();
						break;
					case 0x5c:
						msg = new RequestMoveToLocationInVehicle();
						break;
					case 0x5d:
						msg = new CannotMoveAnymoreInVehicle();
						break;
					case 0x5e:
						msg = new RequestFriendInvite();
						break;
					case 0x5f:
						msg = new RequestAnswerFriendInvite();
						break;
					
					case 0x60:
						msg = new RequestFriendList();
						break;
					case 0x61:
						msg = new RequestFriendDel();
						break;
					case 0x63:
						msg = new RequestQuestList();
						break;
					case 0x64:
						msg = new RequestQuestAbort();
						break;
					case 0x66:
						msg = new RequestPledgeInfo();
						break;
					case 0x67:
						msg = new RequestPledgeExtendedInfo();
						break;
					case 0x68:
						msg = new RequestPledgeCrest();
						break;
					case 0x69:
						msg = new RequestSurrenderPersonally();
						break;
					case 0x6a:
						// // Ride
						break;
					case 0x6b: // send when talking to trainer npc, to show list of available skills
						msg = new RequestAquireSkillInfo();// --> [s] 0xa4;
						break;
					case 0x6c: // send when a skill to be learned is selected
						msg = new RequestAquireSkill();
						break;
					case 0x6d:
						msg = new RequestRestartPoint();
						break;
					case 0x6e:
						msg = new RequestGMCommand();
						break;
					case 0x6f:
						msg = new RequestPartyMatchConfig();
						break;
					case 0x70:
						msg = new RequestPartyMatchList();
						break;
					case 0x71:
						msg = new RequestPartyMatchDetail();
						break;
					case 0x72:
						msg = new RequestCrystallizeItem();
						break;
					case 0x73:
						msg = new RequestPrivateStoreManageSell();
						break;
					case 0x74:
						msg = new SetPrivateStoreListSell();
						break;
					case 0x75:
						// msg = new RequestPrivateStoreManageCancel(data, _client);
						break;
					case 0x76:
						msg = new RequestPrivateStoreQuitSell();
						break;
					case 0x77:
						msg = new SetPrivateStoreMsgSell();
						break;
					case 0x78:
						// // RequestPrivateStoreList
						break;
					case 0x79:
						msg = new RequestPrivateStoreBuy();
						break;
					case 0x7a:
						// // ReviveReply
						break;
					case 0x7b:
						msg = new RequestTutorialLinkHtml();
						break;
					case 0x7c:
						msg = new RequestTutorialPassCmdToServer();
						break;
					case 0x7d:
						msg = new RequestTutorialQuestionMark();
						break;
					case 0x7e:
						msg = new RequestTutorialClientEvent();
						break;
					case 0x7f:
						msg = new RequestPetition();
						break;
					case 0x80:
						msg = new RequestPetitionCancel();
						break;
					case 0x81:
						msg = new RequestGmList();
						break;
					case 0x82:
						msg = new RequestJoinAlly();
						break;
					case 0x83:
						msg = new RequestAnswerJoinAlly();
						break;
					case 0x84:
						msg = new AllyLeave();
						break;
					case 0x85:
						msg = new AllyDismiss();
						break;
					case 0x86:
						msg = new RequestDismissAlly();
						break;
					case 0x87:
						msg = new RequestSetAllyCrest();
						break;
					case 0x88:
						msg = new RequestAllyCrest();
						break;
					case 0x89:
						msg = new RequestChangePetName();
						break;
					case 0x8a:
						msg = new RequestPetUseItem();
						break;
					case 0x8b:
						msg = new RequestGiveItemToPet();
						break;
					case 0x8c:
						msg = new RequestGetItemFromPet();
						break;
					case 0x8e:
						msg = new RequestAllyInfo();
						break;
					case 0x8f:
						msg = new RequestPetGetItem();
						break;
					case 0x90:
						msg = new RequestPrivateStoreManageBuy();
						break;
					case 0x91:
						msg = new SetPrivateStoreListBuy();
						break;
					case 0x92:
						// // RequestPrivateStoreBuyManageCancel
						break;
					case 0x93:
						msg = new RequestPrivateStoreQuitBuy();
						break;
					case 0x94:
						msg = new SetPrivateStoreMsgBuy();
						break;
					case 0x95:
						// // RequestPrivateStoreBuyList
						break;
					case 0x96:
						msg = new RequestPrivateStoreSell();
						break;
					case 0x97:
						// // SendTimeCheckPacket
						break;
					case 0x98:
						// // RequestStartAllianceWar
						break;
					case 0x99:
						// // ReplyStartAllianceWar
						break;
					case 0x9a:
						// // RequestStopAllianceWar
						break;
					case 0x9b:
						// // ReplyStopAllianceWar
						break;
					case 0x9c:
						// // RequestSurrenderAllianceWar
						break;
					case 0x9d:
						msg = new RequestSkillCoolTime();
						/*
						 * if (Config.DEBUG) LOGGER.info("Request Skill Cool Time .. ignored"); msg = null;
						 */
						break;
					case 0x9e:
						msg = new RequestPackageSendableItemList();
						break;
					case 0x9f:
						msg = new RequestPackageSend();
						break;
					case 0xa0:
						msg = new RequestBlock();
						break;
					case 0xa1:
						// // RequestCastleSiegeInfo
						break;
					case 0xa2:
						msg = new RequestSiegeAttackerList();
						break;
					case 0xa3:
						msg = new RequestSiegeDefenderList();
						break;
					case 0xa4:
						msg = new RequestJoinSiege();
						break;
					case 0xa5:
						msg = new RequestConfirmSiegeWaitingList();
						break;
					case 0xa6:
						// // RequestSetCastleSiegeTime
						break;
					case 0xa7:
						msg = new MultiSellChoose();
						break;
					case 0xa8:
						// // NetPing
						break;
					case 0xaa:
						msg = new RequestUserCommand();
						break;
					case 0xab:
						msg = new SnoopQuit();
						break;
					case 0xac: // we still need this packet to handle BACK button of craft dialog
						msg = new RequestRecipeBookOpen();
						break;
					case 0xad:
						msg = new RequestRecipeBookDestroy();
						break;
					case 0xae:
						msg = new RequestRecipeItemMakeInfo();
						break;
					case 0xaf:
						msg = new RequestRecipeItemMakeSelf();
						break;
					
					case 0xb0:
						// msg = new RequestRecipeShopManageList(data, client);
						break;
					case 0xb1:
						msg = new RequestRecipeShopMessageSet();
						break;
					case 0xb2:
						msg = new RequestRecipeShopListSet();
						break;
					case 0xb3:
						msg = new RequestRecipeShopManageQuit();
						break;
					case 0xb4:
						// msg = new SnoopQuit();
						break;
					case 0xb5:
						msg = new RequestRecipeShopMakeInfo();
						break;
					case 0xb6:
						msg = new RequestRecipeShopMakeItem();
						break;
					case 0xb7:
						msg = new RequestRecipeShopManagePrev();
						break;
					case 0xb8:
						msg = new ObserverReturn();
						break;
					case 0xb9:
						msg = new RequestEvaluate();
						break;
					case 0xba:
						msg = new RequestHennaList();
						break;
					case 0xbb:
						msg = new RequestHennaItemInfo();
						break;
					case 0xbc:
						msg = new RequestHennaEquip();
						break;
					case 0xc0:
						// Clan Privileges
						msg = new RequestPledgePower();
						break;
					case 0xc1:
						msg = new RequestMakeMacro();
						break;
					case 0xc2:
						msg = new RequestDeleteMacro();
						break;
					// Manor
					case 0xc3:
						msg = new RequestBuyProcure();
						break;
					case 0xc4:
						msg = new RequestBuySeed();
						break;
					case 0xc5:
						msg = new DlgAnswer();
						break;
					case 0xc6:
						msg = new RequestWearItem();
						break;
					case 0xc7:
						msg = new RequestSSQStatus();
						break;
					case 0xCA:
						msg = new GameGuardReply();
						break;
					case 0xcc:
						msg = new RequestSendFriendMsg();
						break;
					case 0xcd:
						msg = new RequestShowMiniMap();
						break;
					case 0xce: // MSN dialogs so that you dont see them in the console.
						break;
					case 0xcf: // record video
						msg = new RequestRecordInfo();
						break;
					
					case 0xd0:
						/*
						 * int id2 = -1; if(buf.remaining() >= 2) { id2 = buf.getShort() & 0xffff; } else { LOGGER.warn("Client: " + client.toString() + " sent a 0xd0 without the second opcode."); break; }
						 */
						if (opcode2 == -1)
						{
							LOGGER.warn("Client: " + client.toString() + " sent a 0xd0 without the second opcode.");
							break;
						}
						
						if (!NetcoreConfig.getInstance().LIST_ALLOWED_OFFLINE_OPCODES2.contains(opcode2))
						{
							
							if (client.getActiveChar() == null || client.getActiveChar().isOnline() == 0)
							{
								// if not in shutdown
								if (!Shutdown.getInstance().isShutdownStarted())
									LOGGER.warn("ATTENTION: Account " + client.accountName + " is trying to send packet with opcode " + opcode + " (opcode2 = " + opcode2 + ") without enterning in the world (online status is FALSE)..");
								break;
							}
							
						}
						
						switch (opcode2)
						{
							case 1:
								msg = new RequestOustFromPartyRoom();
								break;
							case 2:
								msg = new RequestDismissPartyRoom();
								break;
							case 3:
								msg = new RequestWithdrawPartyRoom();
								break;
							case 4:
								msg = new RequestChangePartyLeader();
								break;
							case 5:
								msg = new RequestAutoSoulShot();
								break;
							case 6:
								msg = new RequestExEnchantSkillInfo();
								break;
							case 7:
								msg = new RequestExEnchantSkill();
								break;
							case 8:
								msg = new RequestManorList();
								break;
							case 9:
								msg = new RequestProcureCropList();
								break;
							case 0x0a:
								msg = new RequestSetSeed();
								break;
							case 0x0b:
								msg = new RequestSetCrop();
								break;
							case 0x0c:
								msg = new RequestWriteHeroWords();
								break;
							case 0x0d:
								msg = new RequestExAskJoinMPCC();
								break;
							case 0x0e:
								msg = new RequestExAcceptJoinMPCC();
								break;
							case 0x0f:
								msg = new RequestExOustFromMPCC();
								break;
							case 0x10:
								msg = new RequestExPledgeCrestLarge();
								break;
							case 0x11:
								msg = new RequestExSetPledgeCrestLarge();
								break;
							case 0x12:
								msg = new RequestOlympiadObserverEnd();
								break;
							case 0x13:
								msg = new RequestOlympiadMatchList();
								break;
							case 0x14:
								msg = new RequestAskJoinPartyRoom();
								break;
							case 0x15:
								msg = new AnswerJoinPartyRoom();
								break;
							case 0x16:
								msg = new RequestListPartyMatchingWaitingRoom();
								break;
							case 0x17:
								msg = new RequestExitPartyMatchingWaitingRoom();
								break;
							case 0x18:
								msg = new RequestGetBossRecord();
								break;
							case 0x19:
								msg = new RequestPledgeSetAcademyMaster();
								break;
							case 0x1a:
								msg = new RequestPledgePowerGradeList();
								break;
							case 0x1b:
								msg = new RequestPledgeMemberPowerInfo();
								break;
							case 0x1c:
								msg = new RequestPledgeSetMemberPowerGrade();
								break;
							case 0x1d:
								msg = new RequestPledgeMemberInfo();
								break;
							case 0x1e:
								msg = new RequestPledgeWarList();
								break;
							case 0x1f:
								msg = new RequestExFishRanking();
								break;
							case 0x20:
								msg = new RequestPCCafeCouponUse();
								break;
							// couldnt find it 0x21 :S
							case 0x22:
								msg = new RequestCursedWeaponList();
								break;
							case 0x23:
								msg = new RequestCursedWeaponLocation();
								break;
							case 0x24:
								msg = new RequestPledgeReorganizeMember();
								break;
							// couldnt find it 0x25 :S
							case 0x26:
								msg = new RequestExMPCCShowPartyMembersInfo();
								break;
							case 0x27:
								msg = new RequestDuelStart();
								break;
							case 0x28:
								msg = new RequestDuelAnswerStart();
								break;
							case 0x29:
								msg = new RequestConfirmTargetItem();
								break;
							case 0x2a:
								msg = new RequestConfirmRefinerItem();
								break;
							case 0x2b:
								msg = new RequestConfirmGemStone();
								break;
							case 0x2c:
								msg = new RequestRefine();
								break;
							case 0x2d:
								msg = new RequestConfirmCancelItem();
								break;
							case 0x2e:
								msg = new RequestRefineCancel();
								break;
							case 0x2f:
								msg = new RequestExMagicSkillUseGround();
								break;
							case 0x30:
								msg = new RequestDuelSurrender();
								break;
							default:
								printDebugDoubleOpcode(opcode, opcode2, buf, state, client);
								break;
						}
						break;
					/*
					 * case 0xee: msg = new RequestChangePartyLeader(); break;
					 */
					default:
						printDebugDoubleOpcode(opcode, 0, buf, state, client);
						break;
				}
				break;
			}
		}
		
		state = null;
		
		return msg;
	}
	
	private void printDebug(final int opcode, final ByteBuffer buf, final GameClientState state, final L2GameClient client)
	{
		if (Config.ENABLE_UNK_PACKET_PROTECTION)
		{
			client.checkUnknownPackets();
		}
		
		if (!NetcoreConfig.getInstance().PACKET_HANDLER_DEBUG)
			return;
		
		// int size = buf.remaining();
		final int v = buf.remaining();
		
		if (Config.DEBUG_UNKNOWN_PACKETS)
		{
			LOGGER.warn("Unknown Packet: " + Integer.toHexString(opcode) + " on State: " + state.name() + " Client: " + client.toString());
		}
		
		byte[] array = new byte[v];
		
		buf.get(array);
		
		if (Config.DEBUG_UNKNOWN_PACKETS)
		{
			LOGGER.warn(Util.printData(array, v));
		}
		
		array = null;
		
	}
	
	private void printDebugDoubleOpcode(final int opcode, final int id2, final ByteBuffer buf, final GameClientState state, final L2GameClient client)
	{
		if (Config.ENABLE_UNK_PACKET_PROTECTION)
		{
			client.checkUnknownPackets();
		}
		
		if (!NetcoreConfig.getInstance().PACKET_HANDLER_DEBUG)
			return;
		
		// int size = buf.remaining();
		final int v = buf.remaining();
		
		if (Config.DEBUG_UNKNOWN_PACKETS)
		{
			LOGGER.warn("Unknown Packet: " + Integer.toHexString(opcode) + ":" + Integer.toHexString(id2) + " on State: " + state.name() + " Client: " + client.toString());
		}
		
		byte[] array = new byte[v];
		
		buf.get(array);
		
		if (Config.DEBUG_UNKNOWN_PACKETS)
		{
			LOGGER.warn(Util.printData(array, v));
		}
		
		array = null;
		
	}
	
	@Override
	public L2GameClient create(final MMOConnection<L2GameClient> con)
	{
		return new L2GameClient(con);
	}
	
	@Override
	public void execute(final ReceivablePacket<L2GameClient> rp)
	{
		rp.getClient().execute(rp);
	}
	
	/*
	 * private void unknownPacketProtection(L2GameClient client) { if(client.getActiveChar() != null && client.checkUnknownPackets()) { UnknownPunish(client); return; } } private void UnknownPunish(L2GameClient client) { switch(Config.UNKNOWN_PACKETS_PUNiSHMENT) { case 1: if(client.getActiveChar()
	 * != null) { GmListTable.broadcastMessageToGMs("Player " + client.getActiveChar().toString() + " flooding unknown packets."); } LOGGER.warn("Player " + client.getActiveChar().toString() + " flooding unknown packets."); break; case 2: LOGGER.warn("PacketProtection: " + client.toString() +
	 * " got kicked due flooding of unknown packets"); if(client.getActiveChar() != null) { GmListTable.broadcastMessageToGMs("Player " + client.getActiveChar().toString() + " flooding unknown packets and got kicked.");
	 * client.getActiveChar().sendMessage("You are will be kicked for unknown packet flooding, GM informed"); client.getActiveChar().closeNetConnection(); } break; case 3: LOGGER.warn("PacketProtection: " + client.toString() + " got banned due flooding of unknown packets");
	 * client.getActiveChar().setAccessLevel(-1); if(client.getActiveChar() != null) { GmListTable.broadcastMessageToGMs("Player " + client.getActiveChar().toString() + " flooding unknown packets and got banned.");
	 * client.getActiveChar().sendMessage("You are banned for unknown packet flooding, GM informed."); client.getActiveChar().closeNetConnection(); } break; } }
	 */
}
