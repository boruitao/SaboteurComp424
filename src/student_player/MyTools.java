package student_player;

import Saboteur.*;
import Saboteur.cardClasses.*;
import java.util.*;

public class MyTools {
	private static final int BOARD_SIZE = 14;
	private static final int MAX_VALUE = 100000;
	private static final int originPos = 5;
	private static boolean[] hiddenRevealed = { false, false, false };
	public static final int[][] hiddenPos = { { originPos + 7, originPos - 2 }, { originPos + 7, originPos },
			{ originPos + 7, originPos + 2 } };
	public static final String[] badTileIdx = { "1", "2", "3", "2_flip", "3_flip", "11", "11_flip", "13", "13_flip",
			"14", "14_flip", "15" };
	public static final String[] goodTileIdx = { "0", "4", "4_flip", "5", "5_flip", "6", "6_flip", "7", "7_flip", "8",
			"9", "9_flip", "10", "12_flip", "12" };
	public static HashMap<Integer, List<SaboteurMove>> map = new HashMap<Integer, List<SaboteurMove>>();
	public static HashSet<String> badTileSet = new HashSet<String>();

	public static void initializeAgent(SaboteurBoardState sbs) {
		for (int i = 0; i < 3; i++) {
			hiddenRevealed[i] = false;
		}
		map = new HashMap<Integer, List<SaboteurMove>>();
		badTileSet = new HashSet<String>();
		for (SaboteurMove move : sbs.getAllLegalMoves()) {
			SaboteurCard card = move.getCardPlayed();
			if (card instanceof SaboteurMap) {
				updateMapValue(1, move);
			} else if (card instanceof SaboteurDestroy) {
				updateMapValue(2, move);
			} else if (card instanceof SaboteurMalus) {
				updateMapValue(3, move);
			} else if (card instanceof SaboteurBonus) {
				updateMapValue(4, move);
			} else if (card instanceof SaboteurTile) {
				updateMapValue(5, move);
			} else {
				updateMapValue(6, move);
			}
		}
		for (String badIdx : badTileIdx) {
			badTileSet.add(badIdx);
		}
	}

	private static void updateMapValue(int key, SaboteurMove move) {
		if (!map.containsKey(key)) {
			List<SaboteurMove> l = new ArrayList<SaboteurMove>();
			l.add(move);
			map.put(key, l);
		} else {
			List<SaboteurMove> l = map.get(key);
			l.add(move);
			map.put(key, l);
		}
	}

	public static double evaluate(SaboteurMove move, SaboteurBoardState sbs, int player) {
		if (sbs.gameOver()) {
			if (sbs.getWinner() == player) {
				return MAX_VALUE - sbs.getTurnNumber();
			} else {
				return -MAX_VALUE;
			}
		}
		int countBadTilesAtHand = 0;
		for (SaboteurCard card : sbs.getCurrentPlayerCards()) {
			if (card instanceof SaboteurTile) {
				SaboteurTile tile = (SaboteurTile) card;
				if (badTileSet.contains(tile.getIdx())) {
					countBadTilesAtHand++;
				}
			}
		}
		double value = 1000;
		SaboteurTile[][] board = sbs.getHiddenBoard();
		SaboteurCard card = move.getCardPlayed();
		if (card instanceof SaboteurMap) {
			int i = getNuggetIndex(sbs);
			if (i == -1) {
				int[] pos = move.getPosPlayed();
				if (!hiddenRevealed[1] && pos[1] == hiddenPos[1][1] && pos[0] == hiddenPos[1][0]) {
					value = value * 140;
				} else if (!hiddenRevealed[2] && pos[1] == hiddenPos[2][1] && pos[0] == hiddenPos[2][0]) {
					value = value * 120;
				} else if (!hiddenRevealed[0] && pos[1] == hiddenPos[0][1] && pos[0] == hiddenPos[0][0]) {
					value = value * 100;
				}
			} else {
				value = 100;
			}
		} else if (card instanceof SaboteurBonus) {
			// if we are frozen we must play this card
			if (!map.containsKey(5)) {
				value = value * 90;
			} else {
				value = 0;
			}
		} else if (card instanceof SaboteurMalus) {
			// if we cannot play a tile card
			if (!map.containsKey(5)) {
				value = value * 30;
			}
			// the larger the turn number, the more likely we play this card
			int turnNumber = sbs.getTurnNumber();
			value += turnNumber * 1300;

			// the closer the current tile to the nugget, the more likely we play this card
			// to freeze the opponent
			int currentShortestDistance = getShortestDistanceOfAllTilesFromNugget(sbs, board);
			value += (BOARD_SIZE * BOARD_SIZE - currentShortestDistance) * 150;
		} else if (card instanceof SaboteurTile) {
			// if there is no path from the origin to the tile we want to play, don't play
			// it.
			if (!isConnectedFromOrigin(board, move)) {
				value = value * 30;
				System.out.println("disconnected: " + move.getPosPlayed()[0] + " " + move.getPosPlayed()[1]);
			} else {
				// if playing the current card will get us closer to the nugget:
				int newDistAfterPlayingCard = getNewDistanceFromNuggetIfMovePlayed(sbs, move);
				int distFromCardMovePos = getDistanceFromMovePosToNugget(sbs, move);
				int currentShortestDist = getShortestDistanceOfAllTilesFromNugget(sbs, board);

				double valueGain = value * 9;
				if (newDistAfterPlayingCard != Integer.MAX_VALUE && distFromCardMovePos != Integer.MAX_VALUE) {
					valueGain = 320 * (BOARD_SIZE * BOARD_SIZE - newDistAfterPlayingCard) + 1000 * (distFromCardMovePos
							- newDistAfterPlayingCard + currentShortestDist - newDistAfterPlayingCard);
				}
				// if playing this card fix a hole:
				SaboteurTile tile = (SaboteurTile) move.getCardPlayed();
				int numOfNeighbours = getNumOfNeighbours(board, move);
				if (!badTileSet.contains(tile.getIdx()) && numOfNeighbours > 1) {
					int[] pos = move.getPosPlayed();
					int nuggetIndex = getNuggetIndex(sbs);
					if (nuggetIndex == -1) {
						nuggetIndex = getDefaultNuggetIndex();
					}
					int disFromNugget = Math.abs(pos[0] - hiddenPos[nuggetIndex][0])
							+ Math.abs(pos[1] - hiddenPos[nuggetIndex][1]);
					value += Math.max((numOfNeighbours - 1) * 10000 + 200 * (BOARD_SIZE * BOARD_SIZE - disFromNugget),
							valueGain);
				}
				// if we can never reach the nugget by playing this move, don't play it
				else {
					value += valueGain;
				}
			}
		} else if (card instanceof SaboteurDestroy) {
			int x = move.getPosPlayed()[0];
			int y = move.getPosPlayed()[1];
			SaboteurTile tile = board[x][y];
			if (badTileSet.contains(tile.getIdx())) {
				value = value * 40;
			} else if (tile.getIdx().contains("8")) {
				value = value * 10;
			} else {
				value = value * 15;
			}
		} else if (card instanceof SaboteurDrop) {
			// drop
			int index = move.getPosPlayed()[0];
			SaboteurCard cardToDrop = sbs.getCurrentPlayerCards().get(index);
			if (cardToDrop instanceof SaboteurMap) {
				if (getNuggetIndex(sbs) == -1)
					value = value * 4;
				else
					value = value * 50;
			} else if (cardToDrop instanceof SaboteurDestroy) {
				value = value * 20;
			} else if (cardToDrop instanceof SaboteurMalus) {
				value = value * 10;
			} else if (cardToDrop instanceof SaboteurBonus) {
				int count = 0;
				for (SaboteurCard cardInHand : sbs.getCurrentPlayerCards()) {
					if (cardInHand instanceof SaboteurBonus) {
						count++;
					}
				}
				if (count > 1) {
					value = value * 20;
				} else {
					value = value * 4;
				}
			} else if (cardToDrop instanceof SaboteurTile) {
				String idx = ((SaboteurTile) cardToDrop).getIdx();
				if (badTileSet.contains(idx)) {
					if (countBadTilesAtHand >= 5) {
						value = value * 50;
					} else {
						value = value * 20;
					}
				} else if (idx.contains("8")) {
					value = value * 10;
				} else {
					value = value * 15;
				}
			}
		}
		return value;
	}

	/**
	 * This method checks if we already know the location of nugget. If not, it
	 * returns -1.
	 */
	public static int getNuggetIndex(SaboteurBoardState sbs) {
		int countFound = 0;
		for (int i = 0; i < 3; i++) {
			String idx = sbs.getHiddenBoard()[hiddenPos[i][0]][hiddenPos[i][1]].getIdx();
			if (idx.equals("nugget")) {
				hiddenRevealed[i] = true;
				System.out.println("Hidden tile revealed! " + i);
				return i;
			} else if (idx.equals("hidden1") || idx.equals("hidden2")) {
				hiddenRevealed[i] = true;
				countFound++;
			}
		}
		if (countFound == 2) {
			for (int i = 0; i < 3; i++) {
				if (sbs.getHiddenBoard()[hiddenPos[i][0]][hiddenPos[i][1]].getIdx().equals("8")) {
					return i;
				}
			}
		}
		return -1;
	}

	public static int getDefaultNuggetIndex() {
		if (!hiddenRevealed[1])
			return 1;
		else if (!hiddenRevealed[2])
			return 2;
		else
			return 0;
	}

	/**
	 * Returns the path length from the nugget to the closest tile on the board.
	 */
	public static int getShortestDistanceOfAllTilesFromNugget(SaboteurBoardState sbs, SaboteurTile[][] board) {
		int nuggetIndex = getNuggetIndex(sbs);
		int dis = Integer.MAX_VALUE;
		if (nuggetIndex == -1) {
			nuggetIndex = getDefaultNuggetIndex();
		}
		HashSet<Integer> intSet = new HashSet<Integer>();
		// form a new tile and see where it can be placed
		for (String idx : goodTileIdx) {
			SaboteurTile dummy = new SaboteurTile(idx);
			for (int[] pos : sbs.possiblePositions(dummy)) {
				if (pos.length == 2) {
					int num = pos[0] + 10 * pos[1];
					intSet.add(num);
				}
			}
		}
		for (int num : intSet) {
			dis = Math.min(dis, getShortestPathFromNugget(board, num % 10, num / 10, nuggetIndex));
		}
		return dis;
	}

	/**
	 * Returns the path length from the nugget to the position we are going to play.
	 */
	public static int getDistanceFromMovePosToNugget(SaboteurBoardState sbs, SaboteurMove move) {
		int nuggetIndex = getNuggetIndex(sbs);
		if (nuggetIndex == -1) {
			nuggetIndex = getDefaultNuggetIndex();
		}
		int[] movePos = move.getPosPlayed();
		;
		int dis = getShortestPathFromNugget(sbs.getHiddenBoard(), movePos[0], movePos[1], nuggetIndex);

		return dis;
	}

	/**
	 * Returns the number of tiles which are next to the tile for the current move.
	 * It is used to determine whether playing this move would fix a hole.
	 */
	public static int getNumOfNeighbours(SaboteurTile[][] board, SaboteurMove move) {
		SaboteurTile tile = (SaboteurTile) move.getCardPlayed();
		int[][] path = tile.getPath();
		int count = 0;
		if (!badTileSet.contains(tile.getIdx())) {
			int[] movePos = move.getPosPlayed();
			// left
			if (path[0][1] == 1 && movePos[1] - 1 >= 0) {
				if (board[movePos[0]][movePos[1] - 1] != null) {
					count++;
				}
			}
			// down
			if (path[1][0] == 1 && movePos[0] + 1 < BOARD_SIZE) {
				if (board[movePos[0] + 1][movePos[1]] != null) {
					count++;
				}
			}
			// right
			if (path[2][1] == 1 && movePos[1] + 1 < BOARD_SIZE) {
				if (board[movePos[0]][movePos[1] + 1] != null) {
					count++;
				}
			}
			// up
			if (path[1][2] == 1 && movePos[0] - 1 >= 0) {
				if (board[movePos[0] - 1][movePos[1]] != null) {
					count++;
				}
			}
		}
		return count;
	}

	/**
	 * Returns the path length from the nugget to the position of the new move if we
	 * play it.
	 */
	public static int getNewDistanceFromNuggetIfMovePlayed(SaboteurBoardState sbs, SaboteurMove move) {
		int nuggetIndex = getNuggetIndex(sbs);
		int dis = Integer.MAX_VALUE;
		if (nuggetIndex == -1) {
			nuggetIndex = getDefaultNuggetIndex();
		}
		SaboteurTile tile = (SaboteurTile) move.getCardPlayed();
		int[][] path = tile.getPath();
		SaboteurTile[][] board = sbs.getHiddenBoard();
		if (!badTileSet.contains(tile.getIdx())) {
			int[] movePos = move.getPosPlayed();
			// left
			if (path[0][1] == 1 && movePos[1] - 1 >= 0) {
				int touchIndex = touchesHiddenObjective(movePos[0], movePos[1] - 1);
				if (board[movePos[0]][movePos[1] - 1] == null) {
					dis = Math.min(dis, getShortestPathFromNugget(board, movePos[0], movePos[1] - 1, nuggetIndex));
				} else if (touchIndex > -1) {
					if (touchIndex == nuggetIndex)
						dis = Math.min(dis, 0);
					else {
						dis = Math.min(dis, 1 + 2 * Math.abs(touchIndex - nuggetIndex));
					}
				}
			}
			// down
			if (path[1][0] == 1 && movePos[0] + 1 < BOARD_SIZE) {
				int touchIndex = touchesHiddenObjective(movePos[0] + 1, movePos[1]);
				if (board[movePos[0] + 1][movePos[1]] == null) {
					dis = Math.min(dis, getShortestPathFromNugget(board, movePos[0] + 1, movePos[1], nuggetIndex));
				} else if (touchIndex > -1) {
					if (touchIndex == nuggetIndex)
						dis = Math.min(dis, 0);
					else {
						dis = Math.min(dis, 1 + 2 * Math.abs(touchIndex - nuggetIndex));
					}
				}
			}
			// right
			if (path[2][1] == 1 && movePos[1] + 1 < BOARD_SIZE) {
				int touchIndex = touchesHiddenObjective(movePos[0], movePos[1] + 1);
				if (board[movePos[0]][movePos[1] + 1] == null) {
					dis = Math.min(dis, getShortestPathFromNugget(board, movePos[0], movePos[1] + 1, nuggetIndex));
				} else if (touchIndex > -1) {
					if (touchIndex == nuggetIndex)
						dis = Math.min(dis, 0);
					else {
						dis = Math.min(dis, 1 + 2 * Math.abs(touchIndex - nuggetIndex));
					}
				}
			}
			// up
			if (path[1][2] == 1 && movePos[0] - 1 >= 0) {
				int touchIndex = touchesHiddenObjective(movePos[0] - 1, movePos[1]);
				if (board[movePos[0] - 1][movePos[1]] == null) {
					dis = Math.min(dis, getShortestPathFromNugget(board, movePos[0] - 1, movePos[1], nuggetIndex));
				} else if (touchIndex > -1) {
					if (touchIndex == nuggetIndex)
						dis = Math.min(dis, 0);
					else {
						dis = Math.min(dis, 1 + 2 * Math.abs(touchIndex - nuggetIndex));
					}
				}
			}
		}
		return dis;
	}

	public static int touchesHiddenObjective(int x, int y) {
		for (int i = 0; i < hiddenPos.length; i++) {
			if (hiddenPos[i][0] == x && hiddenPos[i][1] == y)
				return i;
		}
		return -1;
	}

	public static int getShortestPathFromNugget(SaboteurTile[][] board, int x, int y, int nuggetIndex) {
		int row[] = { -1, 0, 0, 1 };
		int col[] = { 0, -1, 1, 0 };
		boolean[][] visited = new boolean[BOARD_SIZE][BOARD_SIZE];
		Queue<Node> q = new ArrayDeque<Node>();
		visited[x][y] = true;
		q.add(new Node(x, y, 0));

		int i = hiddenPos[nuggetIndex][0];
		int j = hiddenPos[nuggetIndex][1];
		int minDis = Integer.MAX_VALUE;
		while (!q.isEmpty()) {
			Node node = q.poll();
			x = node.x;
			y = node.y;
			int dis = node.dist;

			if (x == i && y == j) {
				minDis = dis;
				break;
			}

			for (int k = 0; k < 4; k++) {
				if (isValid(board, visited, x + row[k], y + col[k])) {
					visited[x + row[k]][y + col[k]] = true;
					q.add(new Node(x + row[k], y + col[k], dis + 1));
				}
			}
		}
		return minDis;
	}

	public static boolean isConnectedFromOrigin(SaboteurTile[][] board, SaboteurMove move) {
		int row[] = { -1, 0, 0, 1 };
		int col[] = { 0, -1, 1, 0 };
		int[] pos = move.getPosPlayed();
		List<int[]> neighbours = new ArrayList<int[]>();
		for (int k = 0; k < 4; k++) {
			int x = pos[0] + row[k];
			int y = pos[1] + col[k];
			if (x >= 0 && y >= 0 && x < BOARD_SIZE && y < BOARD_SIZE) {
				if (board[x][y] != null && !badTileSet.contains(board[x][y].getIdx())) {
					int[] coor = { x, y };
					neighbours.add(coor);
				}
			}
		}
		boolean[][] visited = new boolean[BOARD_SIZE][BOARD_SIZE];
		Queue<Node> q = new ArrayDeque<Node>();
		int i = originPos;
		int j = originPos;
		visited[i][j] = true;
		q.add(new Node(i, j, 0));

//		int minDis = Integer.MAX_VALUE;
		for (int[] coor : neighbours) {
			while (!q.isEmpty()) {
				Node node = q.poll();
				i = node.x;
				j = node.y;
//				int dis = node.dist;

				if (coor[0] == i && coor[1] == j) {
//					minDis = dis;
					return true;
				}

				for (int k = 0; k < 4; k++) {
					if (isAConnectedTile(board, visited, i + row[k], j + col[k])) {
						visited[i + row[k]][j + col[k]] = true;
						q.add(new Node(i + row[k], j + col[k], 0));
					}
				}
			}
		}
		return false;
	}

	public static boolean isValid(SaboteurTile[][] board, boolean visited[][], int x, int y) {
		return (x < BOARD_SIZE && y < BOARD_SIZE && x >= 0 && y >= 0 && !visited[x][y]
				&& (board[x][y] == null || touchesHiddenObjective(x, y) > -1));
	}

	public static boolean isAConnectedTile(SaboteurTile[][] board, boolean visited[][], int x, int y) {
		return (x < BOARD_SIZE && y < BOARD_SIZE && x >= 0 && y >= 0 && !visited[x][y] && board[x][y] != null
				&& !badTileSet.contains(board[x][y].getIdx()));
	}

	public static double getSomething() {
		return Math.random();
	}
}