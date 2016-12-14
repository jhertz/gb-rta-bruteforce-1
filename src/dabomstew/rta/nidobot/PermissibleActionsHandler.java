package dabomstew.rta.nidobot;

import java.io.IOException;
import java.util.List;
import java.util.LinkedList;
import dabomstew.rta.FileFunctions;

public class PermissibleActionsHandler {

    public static final int A = 0x01;
    public static final int B = 0x02;
    public static final int SELECT = 0x04;
    public static final int START = 0x08;

    public static final int RIGHT = 0x10;
    public static final int LEFT = 0x20;
    public static final int UP = 0x40;
    public static final int DOWN = 0x80;

    private static int[][] grassCalcViridian, grassCalcRoute22, grassCalcVermillion, grassCalcRoute11;


    // first rect to include, use more if needed
    //top left: 0x17, 0x01
    //bottom left: 0x19, 0x01
    //top right: 0x17, 0x06
    //bottom right 0x19, 0x06

    //         spm.exclude(0x18, 0x04); // sign
    // this is the tile right behind the DF trainer
    public static final byte DEATHFLY_Y = 0x13; //D361
    public static final byte DEATHFLY_X = 0x01; //D362

    public static List<Integer> whereDoWeGo(int x, int y) {
        // use logic here to make sure we don't wallbonk

        List<Integer> starting = new LinkedList<>();

        if(x == 0x14 && y == 0x01){ //walk into the deathfly
            starting.add(UP);
            return starting;
        }

        if(y < 0x17){ //dont walk further down
            starting.add(DOWN);
        }

        if(x > 0x01){ // dont wallbonk
            starting.add(LEFT);
        }

        if(x < 0x02){
            starting.add(RIGHT);
        }

        if(y < 0x13){
            starting.add(UP);
        }

        return starting;
    }
}
