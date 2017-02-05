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
    public static final byte DEATHFLY_Y = 19; //D361
    public static final byte DEATHFLY_X = 1; //D362

    public static List<Integer> whereDoWeGo(int x, int y) {

        List<Integer> starting = new LinkedList<>();

        System.out.println("our x:" + x + " y:" + y);
        if(x == 20 && y == 1){ //walk into the deathfly
        //    System.out.println("adding up, since were about to hit the goal");
            starting.add(UP);
            return starting;
        }

        //don't trainer bonk
        if( x != 20 && y != 2) {
         //   System.out.println("adding up");
            starting.add(UP);
        }

        if(y < 25){ //dont walk further down
          //  System.out.println("adding down");
            starting.add(DOWN);
        }

        if(x > 1){ // dont wallbonk
          //  System.out.println("adding left");
            starting.add(LEFT);
        }

        if(x < 2){
         //   System.out.println("adding right");
            starting.add(RIGHT);
        }



        return starting;
    }
}
