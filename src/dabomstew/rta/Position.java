package dabomstew.rta;

public class Position {
	
	public Position(int map, int x, int y) {
		super();
		this.map = map;
		this.x = x;
		this.y = y;
	}


	public int map;
	public int x;
	public int y;


	@Override
	public boolean equals(Object o){
		if(o instanceof Position) {
			Position pos = (Position) o;
			return ((this.x == pos.x) && (this.y == pos.y) && (this.map == pos.map));
		}
		return false;
	}

	@Override
	public int hashCode() {
		return toString().hashCode();
	}

    @Override
	public String toString() {
        return "[" + map + "#" + x + "," + y + "]";
    }
}
