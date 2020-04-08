package student_player;

import boardgame.*;
import java.util.*;
import Saboteur.*;

public class HillClimbing {
	public static Move getOptimalMove(SaboteurBoardState sbs) {
		double bestValue = Double.MIN_VALUE;
		List<SaboteurMove> listOfMoves = new ArrayList<SaboteurMove>();
		MyTools.initializeAgent(sbs);
		for (SaboteurMove move : sbs.getAllLegalMoves()) {
			double value = MyTools.evaluate(move, sbs, sbs.getTurnPlayer());
			System.out.println("Move : " + move.getCardPlayed().getName() + " value: " + value);
			if (value > bestValue) {
				listOfMoves = new ArrayList<SaboteurMove>();
				listOfMoves.add(move);
				bestValue = value;
			} else if (value == bestValue) {
				listOfMoves.add(move);
			}
		}
		if (listOfMoves.size() == 0) {
			return sbs.getRandomMove();
		} else {
			int random = new Random().nextInt(listOfMoves.size());
			return listOfMoves.get(random);
		}
	}
}
