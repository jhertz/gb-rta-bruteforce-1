package dabomstew.rta.nidobot;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import dabomstew.rta.Position;

public class StartingPositionManager implements Iterable<Position> {

	private boolean[][] startingPositions;

	public StartingPositionManager() {
		startingPositions = new boolean[360][340];
	}

	public void includeRect(int left, int top, int right, int bottom) {
		for (int y = top; y <= bottom; y++) {
			for (int x = left; x <= right; x++) {
				startingPositions[y][x] = true;
			}
		}
	}

	public void excludeRect(int left, int top, int right, int bottom) {
		for (int y = top; y <= bottom; y++) {
			for (int x = left; x <= right; x++) {
				startingPositions[y][x] = false;
			}
		}
	}

	public void exclude(int x, int y) {
		startingPositions[y][x] = false;
	}

	public void excludeNpc(int x, int y) {
		excludeRect(x - 5, y - 4, x + 4, y + 4);
	}

	@Override
	public Iterator<Position> iterator() {
		List<Position> tmpList = new ArrayList<>();
		for (int y = 0; y < 360; y++) {
			for (int x = 0; x < 340; x++) {
				if(startingPositions[y][x]){
					tmpList.add(new Position(DeathFlyBot.MAP_ID, x, y));
				}
			}
		}
		return tmpList.iterator();
	}
	
}
