package me.desht.chesscraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import me.desht.chesscraft.ExpectResponse.ExpectAction;
import me.desht.chesscraft.exceptions.ChessException;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerListener;

import chesspresso.Chess;
import chesspresso.move.IllegalMoveException;

public class ChessPlayerListener extends PlayerListener {
	private ChessCraft plugin;
	private static final Map<String,List<String>> expecting = new HashMap<String,List<String>>();
	
	public ChessPlayerListener(ChessCraft plugin) {
		this.plugin = plugin;
	}
	
	@Override
	public void onPlayerInteract(PlayerInteractEvent event) {
		if (event.isCancelled()) return;
			
		Player player = event.getPlayer();
		
		try {
			Block b = event.getClickedBlock();
			if (b == null) return;
			if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
				Location loc = b.getLocation();
				BoardView bv;
				if (plugin.expecter.isExpecting(player, ExpectAction.BoardCreation)) {
					plugin.expecter.cancelAction(player, ExpectAction.BoardCreation);
					plugin.statusMessage(player, "Board creation cancelled.");
				} else if ((bv = plugin.onChessBoard(loc)) != null) {
					deprecationWarning(player);
					boardClicked(player, loc, bv);
				} else if ((bv = plugin.aboveChessBoard(loc)) != null) {
					deprecationWarning(player);
					pieceClicked(player, loc, bv);
				} else {
					// nothing?
				}
			} else if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
				if (plugin.expecter.isExpecting(player, ExpectAction.BoardCreation)) {
					ExpectBoardCreation a = (ExpectBoardCreation) plugin.expecter.getAction(player, ExpectAction.BoardCreation);
					a.setLocation(b.getLocation());
					plugin.expecter.handleAction(player, ExpectAction.BoardCreation);
					return;
				} else {
					BoardView bv = plugin.partOfChessBoard(b.getLocation());
					if (bv != null && b.getState() instanceof Sign) {
						signClicked(player, b, bv);
					} 
				}
			}
		} catch (ChessException e) {
			plugin.errorMessage(player, e.getMessage());
			if (plugin.expecter.isExpecting(player, ExpectAction.BoardCreation)) {
				plugin.expecter.cancelAction(player, ExpectAction.BoardCreation);
				plugin.errorMessage(player, "Board creation cancelled.");
			}
		} catch (IllegalMoveException e) {
			cancelMove(event.getClickedBlock().getLocation());
			plugin.errorMessage(player, e.getMessage() + ".  Move cancelled.");
		}
	}

	private void signClicked(Player player, Block b, BoardView bv) throws ChessException {
		Sign s = (Sign) b.getState();
		Game game = bv.getGame();
		if (s.getLine(1).endsWith("Create Game")) {
			plugin.getCommandExecutor().tryCreateGame(player, null, bv.getName());
		} else if (s.getLine(1).endsWith("Start Game")) {
			if (game != null)
				game.start(player.getName());
		} else if (s.getLine(1).endsWith("Resign")) {
			if (game != null)
				game.resign(player.getName());
		} else if (s.getLine(1).endsWith("Offer Draw")) {
			if (game != null)
				plugin.getCommandExecutor().tryOfferDraw(player,game);
		} else if (s.getLine(1).endsWith("Show Info")) {
			if (game != null)
				plugin.getCommandExecutor().showGameDetail(player, game.getName());
		} else if (s.getLine(1).endsWith("Invite Player")) {
			if (game != null && (game.getPlayerWhite().isEmpty() || game.getPlayerBlack().isEmpty()))
				plugin.statusMessage(player, "Type &f/chess invite <playername>&- to invite someone");
		} else if (s.getLine(1).endsWith("Invite ANYONE")) {
			if (game != null && (game.getPlayerWhite().isEmpty() || game.getPlayerBlack().isEmpty()))
				game.inviteOpen(player.getName());
		} else if (s.getLine(1).endsWith("Teleport Out")) {
			plugin.getCommandExecutor().tryTeleportOut(player);
		}
	}

	private void deprecationWarning(Player player) {
		plugin.statusMessage(player, "&4WARNING: &-right-clicking is deprecated and will be removed soon.");
		String wand = plugin.getConfiguration().getString("wand_item");
		int wandId = new MaterialWithData(wand).material;
		plugin.statusMessage(player, "Left-click while holding " + Material.getMaterial(wandId) + " to select & move.");
	}

	@Override
	public void onPlayerAnimation(PlayerAnimationEvent event) {	
		Player player = event.getPlayer();
		
		Block targetBlock = null;
		
		try {
			if (event.getAnimationType() == PlayerAnimationType.ARM_SWING) {
				String wand = plugin.getConfiguration().getString("wand_item");
				int wandId = new MaterialWithData(wand).material;
				if (player.getItemInHand().getTypeId() == wandId) {
					HashSet<Byte> transparent = new HashSet<Byte>();
					transparent.add((byte) 0);	// air
					transparent.add((byte) 20);	// glass
					targetBlock = player.getTargetBlock(transparent, 100);
					Location loc = targetBlock.getLocation();
					BoardView bv;
					if ((bv = plugin.onChessBoard(loc)) != null) {
						boardClicked(player, loc, bv);
					} else if ((bv = plugin.aboveChessBoard(loc)) != null) {
						pieceClicked(player, loc, bv);
					} else if ((bv = plugin.partOfChessBoard(loc)) != null) {
						if (bv.isControlPanel(loc)) {
							Location corner = bv.getBounds().getUpperSW();
							Location loc2 = new Location(corner.getWorld(), corner.getX() - 4 * bv.getSquareSize(), corner.getY() + 1, corner.getZ() - 2.5);
							player.teleport(loc2);
						}
					}
				}
			}
		} catch (ChessException e) {
			plugin.errorMessage(player, e.getMessage());
		} catch (IllegalMoveException e) {
			if (targetBlock != null) {
				cancelMove(targetBlock.getLocation());
			}
			plugin.errorMessage(player, e.getMessage() + ".  Move cancelled.");
		}
		
	}
	
	
	private void cancelMove(Location loc) {
		BoardView bv = plugin.onChessBoard(loc);
		if (bv == null) 
			bv = plugin.aboveChessBoard(loc);
		if (bv != null && bv.getGame() != null) {
			bv.getGame().setFromSquare(Chess.NO_SQUARE);
		}
	}

	private void pieceClicked(Player player, Location loc, BoardView bv) throws IllegalMoveException, ChessException {
		Game game = bv.getGame();
		if (game == null || game.getState() != GameState.RUNNING)
			return;
		
		if (game.isPlayerToMove(player.getName())) {
			if (game.getFromSquare() == Chess.NO_SQUARE) {
				int sqi = game.getView().getSquareAt(loc);
				int colour = game.getPosition().getColor(sqi);
				if (colour == game.getPosition().getToPlay()) {
					game.setFromSquare(sqi);
					int piece = game.getPosition().getPiece(sqi);
					String what = ChessCraft.pieceToStr(piece).toUpperCase();
					plugin.statusMessage(player, "Selected your &f" + what + "&- at &f" + Chess.sqiToStr(sqi) + "&-.");
					plugin.statusMessage(player, "&5-&- Left-click a square or another piece to move your &f" + what);
					plugin.statusMessage(player, "&5-&- Left-click the &f" + what + "&- again to cancel.");
				}
			} else {
				int sqi = game.getView().getSquareAt(loc);
				if (sqi == game.getFromSquare()) {
					game.setFromSquare(Chess.NO_SQUARE);
					plugin.statusMessage(player, "Move cancelled.");
				} else if (sqi >= 0 && sqi < Chess.NUM_OF_SQUARES) {
					game.doMove(player.getName(), sqi);
					plugin.statusMessage(player, "You played " + game.getPosition().getLastMove().getLAN() + ".");
				}
			}
		} else if (game.isPlayerInGame(player.getName())) {
			plugin.errorMessage(player, "It is not your turn!");
		}
	}
	
	private void boardClicked(Player player, Location loc, BoardView bv)
			throws IllegalMoveException, ChessException {
		int sqi = bv.getSquareAt(loc);
		Game game = bv.getGame();
		if (game != null && game.getFromSquare() != Chess.NO_SQUARE) {
			game.doMove(player.getName(), sqi);
			plugin.statusMessage(player, "You played &f[" + game.getPosition().getLastMove().getLAN() + "]&-.");
		} else {
			plugin.statusMessage(player, "Square &6[" + Chess.sqiToStr(sqi) + "]&-, board &6" + bv.getName() + "&-");
		}
	}

	static void expectingClick(Player p, String name, String style) {
		List<String> list = new ArrayList<String>();
		list.add(name);
		list.add(style);
		expecting.put(p.getName(), list);
	}
}
