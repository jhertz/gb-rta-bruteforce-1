package dabomstew.rta.nidobot;

import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import mrwint.gbtasgen.Gb;
import dabomstew.rta.RedBlueAddr;
import dabomstew.rta.Encounter;
import dabomstew.rta.Func;
import dabomstew.rta.GBMemory;
import dabomstew.rta.GBWrapper;

public class EncounterCheckThreadDeathFly extends Thread {

    public static final int A = 0x01;
    public static final int B = 0x02;
    public static final int SELECT = 0x04;
    public static final int START = 0x08;

    public static final int RIGHT = 0x10;
    public static final int LEFT = 0x20;
    public static final int UP = 0x40;
    public static final int DOWN = 0x80;

    private PositionEnteringGrass peg;
    private Gb gb;
    private GBMemory mem;
    private GBWrapper wrap;
    private int bCost;
    private PrintStream ps;
    private boolean[] threadsRunning;
    private int useIdx;

    public EncounterCheckThreadDeathFly(PositionEnteringGrass peg, Gb gb, GBMemory mem, GBWrapper wrap, int bCost,
                                        PrintStream ps, boolean[] threadsRunning, int useIdx) {
        this.peg = peg;
        this.gb = gb;
        this.mem = mem;
        this.wrap = wrap;
        this.bCost = bCost;
        this.ps = ps;
        this.threadsRunning = threadsRunning;
        this.useIdx = useIdx;
    }

    @Override
    public void run() {
        try {
            int pathCost = bCost;
            if (pathCost >= DeathFlyBot.maxCostOfPath) {
                return;
            }
            if (!DeathFlyBot.startPositionsCosts.containsKey(peg.rngState)) {
                DeathFlyBot.startPositionsCosts.put(peg.rngState, pathCost);
                DeathFlyBot.startPositionsEncs.put(peg.rngState, new ArrayList<String>());
                ByteBuffer curState = peg.savedState;
                gb.loadState(curState);

                //walk into the death
                wrap.injectRBInput(UP);
                wrap.advanceToAddress(RedBlueAddr.newBattleAddr);

                // encounter found?
                if (mem.getHRA() >= 0 && mem.getHRA() <= 14) { // 24
                    //  got encounter?
                    // TODO: make sure this doesnt get hit by trainer
                    System.out.println("we found a deathfly!");
                    String rngAtEnc = mem.getRNGState();
                    wrap.advanceFrame();
                    wrap.advanceFrame();
                    Encounter enc = new Encounter(mem.getEncounterSpecies(), mem.getEncounterLevel(),
                            mem.getEncounterDVs(), mem.getRNGStateHRAOnly());

                    int totalEncCost = pathCost + 17;
                    String encRep = enc.toString();
                    System.out.println("got far enough that this hack mattered");
                    DeathFlyBot.startPositionsEncs.get(peg.rngState).add(
                            encRep + "/" + 17 + "/" + rngAtEnc + "/" + UP); //XXX: this might be wrong
                    synchronized (DeathFlyBot.encountersCosts) {
                        if (!DeathFlyBot.encountersCosts.containsKey(encRep)
                                || DeathFlyBot.encountersCosts.get(encRep) > totalEncCost) {
                            ps.printf(
                                    "inputs %s step %d cost %d encounter: species %d lv%d DVs %04X rng %s encrng %s\n",
                                    peg.path, 1, totalEncCost, enc.species, enc.level, enc.dvs,
                                    enc.battleRNG, rngAtEnc);
                            DeathFlyBot.encountersCosts.put(encRep, totalEncCost);
                        }
                    }
                    // some species
                    // caterpie 0x7B
                    // weedle 0x70}

                    // check if the enc.species is a good encounter
                    if (enc.species == 0x7B || enc.species == 0x70 ) { //&& enc.level == 15
                        DeathFlyBot.logLN("POTENTIAL BUG ENCOUNTER FOUND!");
                        DeathFlyBot.logF(
                                "inputs %s step %d cost %d encounter: species %d lv%d DVs %04X rng %s encrng %s\n",
                                peg.path, 1, totalEncCost, enc.species, enc.level, enc.dvs, enc.battleRNG,
                                rngAtEnc);

                        //woot, we found a way to get an encounter on the tile w/ the rng state, record it
                        if (pathCost < DeathFlyBot.startPositionsCosts.get(peg.rngState)) {
                            DeathFlyBot.startPositionsCosts.put(peg.rngState, pathCost);
                            for (String stateEnc : DeathFlyBot.startPositionsEncs.get(peg.rngState)) {
                                String[] encBits = stateEnc.split("\\/");
                                int cost = Integer.parseInt(encBits[4]);
                                int step = Integer.parseInt(encBits[6]);
                                encRep = encBits[0] + "/" + encBits[1] + "/" + encBits[2] + "/" + encBits[3];
                                totalEncCost = pathCost + cost;
                                synchronized (DeathFlyBot.encountersCosts) {
                                    if (DeathFlyBot.encountersCosts.get(encRep) > totalEncCost) {
                                        ps.printf("inputs %s step %d cost %d encounter: species %s lv%s DVs %s rng %s encrng %s\n",
                                                peg.path, step + 1, totalEncCost, encBits[0], encBits[1], encBits[2], encBits[3],
                                                encBits[5]);
                                        DeathFlyBot.encountersCosts.put(encRep, totalEncCost);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            synchronized (threadsRunning) {
                threadsRunning[useIdx] = false;
            }
        }
    }

}
