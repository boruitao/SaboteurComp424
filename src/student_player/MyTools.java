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
	public static final String[] badTileIdx = { "1", "2", "3", "4", "2_flip", "3_flip", "4_flip", "11", "11_flip", "13",
			"13_flip", "14", "14_flip", "15" };

	public static HashMap<Integer, List<SaboteurMove>> map = new HashMap<Integer, List<SaboteurMove>>();
	public static HashSet<String> set = new HashSet<String>();

	public static HashMap<Integer, List<SaboteurMove>> populateHashMap(SaboteurBoardState sbs) {
		map = new HashMap<Integer, List<SaboteurMove>>();
		set = new HashSet<String>();
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
			set.add(badIdx);
		}
		return map;
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
				if (set.contains(tile.getIdx())) {
					countBadTilesAtHand++;
				}
			}
		}
		double value = 1000;
		SaboteurCard card = move.getCardPlayed();
		if (card instanceof SaboteurMap) {
			if (getNuggetIndex(sbs) == -1) {
				value = value * 100;
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
			int currentShortestDistance = getShortestDistanceOfAllTilesFromNugget(sbs);
			value += (BOARD_SIZE * BOARD_SIZE - currentShortestDistance) * 150;
		} else if (card instanceof SaboteurTile) {
			// if there is no path from the origin to the tile we want to play, don't play
			// it.
			if (!isConnectedFromOrigin(sbs.getHiddenBoard(), move)) {
				value = value * 30;
				System.out.println("disconnected: " + move.getPosPlayed()[0] + " " + move.getPosPlayed()[1]);
			} else {
				// if playing the current card will get us closer to the nugget:
				int newDistAfterPlayingCard = getNewDistanceFromNuggetIfMovePlayed(sbs, move);
				int distFromCardMovePos = getDistanceFromMovePosToNugget(sbs, move);
				int currentShortestDist = getShortestDistanceOfAllTilesFromNugget(sbs);
				System.out.println("");
				System.out.println("getNewDistanceFromNuggetIfMovePlayed: " + newDistAfterPlayingCard + " "
						+ move.getCardPlayed().getName());
				System.out.println("getDistanceFromMovePosToNugget: " + distFromCardMovePos);
				System.out.println("currentShortestDist: " + currentShortestDist);
				System.out.println(move.getPosPlayed()[0] + " " + move.getPosPlayed()[1]);
				// if we can never reach the nugget by playing this move, don't play it
				if (newDistAfterPlayingCard == Integer.MAX_VALUE || distFromCardMovePos == Integer.MAX_VALUE)
					value = value * 10;
				else {
					value += 320 * (BOARD_SIZE * BOARD_SIZE - newDistAfterPlayingCard);
					value += 1000 * (distFromCardMovePos - newDistAfterPlayingCard + currentShortestDist
							- newDistAfterPlayingCard);
				}
			}
		} else if (card instanceof SaboteurDestroy) {
			SaboteurTile[][] board = sbs.getHiddenBoard();
			int x = move.getPosPlayed()[0];
			int y = move.getPosPlayed()[1];
			SaboteurTile tile = board[x][y];
			if (set.contains(tile.getIdx())) {
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
					value = value * 20;
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
				if (set.contains(idx)) {
					if (countBadTilesAtHand >= 5) {
						value = value * 30;
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

	public static int getNuggetIndex(SaboteurBoardState sbs) {
		int countFound = 0;
		for (int i = 0; i < 3; i++) {
			if (sbs.getHiddenBoard()[hiddenPos[i][0]][hiddenPos[i][1]].getIdx().equals("nugget")) {
				hiddenRevealed[i] = true;
				System.out.println("Hidden tile revealed! " + i);
				return i;
			} else if (sbs.getHiddenBoard()[hiddenPos[i][0]][hiddenPos[i][1]].getIdx().equals("hidden1")
					|| sbs.getHiddenBoard()[hiddenPos[i][0]][hiddenPos[i][1]].getIdx().equals("hidden2")) {
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

	public static int getShortestDistanceOfAllTilesFromNugget(SaboteurBoardState sbs) {
		int nuggetIndex = getNuggetIndex(sbs);
		int dis = Integer.MAX_VALUE;
		if (nuggetIndex == -1) {
			nuggetIndex = getDefaultNuggetIndex();
		}
		// form a new "8" tile and see where it can be placed
		SaboteurTile dummy = new SaboteurTile("8");
		for (int[] pos : sbs.possiblePositions(dummy)) {
			if (pos.length == 2) {
				dis = Math.min(dis, getShortestPathFromNugget(sbs.getHiddenBoard(), pos[0], pos[1], nuggetIndex));
			}
		}
		return dis;
	}

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

	public static int getNewDistanceFromNuggetIfMovePlayed(SaboteurBoardState sbs, SaboteurMove move) {
		int nuggetIndex = getNuggetIndex(sbs);
		int dis = Integer.MAX_VALUE;
		if (nuggetIndex == -1) {
			nuggetIndex = getDefaultNuggetIndex();
		}
		SaboteurTile tile = (SaboteurTile) move.getCardPlayed();
		int[][] path = tile.getPath();
		SaboteurTile[][] board = sbs.getHiddenBoard();
		if (!set.contains(tile.getIdx())) {
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

//	public static int getDistanceFromNugget(int x, int y, int nuggetIndex) {
//		return Math.abs(x - hiddenPos[nuggetIndex][0]) + Math.abs(y - hiddenPos[nuggetIndex][1]);
//	}

//	public static int getShortestPathFromNugget(SaboteurTile[][] board, int visited[][], int x, int y, int nuggetIndex,
//			int min_dist, int dist) {
//		if (hiddenPos[nuggetIndex][0] == x && hiddenPos[nuggetIndex][1] == y) {
//			return Integer.min(dist, min_dist);
//		}
//		visited[x][y] = 1;
//		if (isValid(x + 1, y) && isSafe(board, visited, x + 1, y)) {
//			min_dist = getShortestPathFromNugget(board, visited, x + 1, y, nuggetIndex, min_dist, dist + 1);
//		}
//		if (isValid(x, y + 1) && isSafe(board, visited, x, y + 1)) {
//			min_dist = getShortestPathFromNugget(board, visited, x, y + 1, nuggetIndex, min_dist, dist + 1);
//		}
//		if (isValid(x - 1, y) && isSafe(board, visited, x - 1, y)) {
//			min_dist = getShortestPathFromNugget(board, visited, x - 1, y, nuggetIndex, min_dist, dist + 1);
//		}
//		if (isValid(x, y - 1) && isSafe(board, visited, x, y - 1)) {
//			min_dist = getShortestPathFromNugget(board, visited, x, y - 1, nuggetIndex, min_dist, dist + 1);
//		}
//		visited[x][y] = 0;
//		return min_dist;
//	}

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
				if (board[x][y] != null && !set.contains(board[x][y].getIdx())) {
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
				&& !set.contains(board[x][y].getIdx()));
	}

//	public static boolean isValid(int x, int y) {
//		return (x < BOARD_SIZE && y < BOARD_SIZE && x >= 0 && y >= 0);
//	}
//
//	public static boolean isSafe(SaboteurTile[][] board, int visited[][], int x, int y) {
//		return visited[x][y] == 0 && (board[x][y] == null || touchesHiddenObjective(x, y) > -1);
//	}

	public static double getSomething() {
		return Math.random();
	}
}