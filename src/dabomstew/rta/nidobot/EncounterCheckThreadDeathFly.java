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
            int pathACost = Func.aCount(peg.path, peg.path.length()) * 2;
            int pathCost = bCost + pathACost;
            if (pathCost >= DeathFlyBot.maxCostOfPath) {
                return;
            }
            if (!DeathFlyBot.startPositionsCosts.containsKey(peg.rngState)) {
                DeathFlyBot.startPositionsCosts.put(peg.rngState, pathCost);
                DeathFlyBot.startPositionsEncs.put(peg.rngState, new ArrayList<String>());
                int oogDir = DOWN;
                ByteBuffer curState = peg.savedState;
                gb.loadState(curState);
                int maxSteps = Math.min((DeathFlyBot.maxCostOfPath - pathCost) / 17, DeathFlyBot.maxStepsInGrassArea);
                for (int step = 0; step < maxSteps; step++) {
                    int numSteps = step + 1;
                    if (step % 2 == 1) {
                        numSteps++;
                    }
                    int stepsFrameCost = numSteps * 17;

                    if (pathCost + stepsFrameCost >= DeathFlyBot.maxCostOfPath) {
                        // too long, not
                        // interested
                        break;
                    }
                    // first try stepping
                    // into
                    // the grass
                    wrap.injectRBInput(RIGHT);
                    wrap.advanceWithJoypadToAddress(RIGHT, RedBlueAddr.newBattleAddr);

                    // encounter found?
                    if (mem.getHRA() >= 0 && mem.getHRA() <= 14) { // 24
                        // ok got possible
                        // FFEF encounter,
                        // note what it is
                        String rngAtEnc = mem.getRNGState();
                        wrap.advanceFrame();
                        wrap.advanceFrame();
                        Encounter enc = new Encounter(mem.getEncounterSpecies(), mem.getEncounterLevel(),
                                mem.getEncounterDVs(), mem.getRNGStateHRAOnly());

                        int totalEncCost = pathCost + stepsFrameCost;
                        String encRep = enc.toString();
                        DeathFlyBot.startPositionsEncs.get(peg.rngState).add(
                                encRep + "/" + stepsFrameCost + "/" + rngAtEnc + "/" + step);
                        synchronized (DeathFlyBot.encountersCosts) {
                            if (!DeathFlyBot.encountersCosts.containsKey(encRep)
                                    || DeathFlyBot.encountersCosts.get(encRep) > totalEncCost) {
                                ps.printf(
                                        "inputs %s step %d cost %d encounter: species %d lv%d DVs %04X rng %s encrng %s\n",
                                        peg.path, step + 1, totalEncCost, enc.species, enc.level, enc.dvs,
                                        enc.battleRNG, rngAtEnc);
                                DeathFlyBot.encountersCosts.put(encRep, totalEncCost);
                            }
                        }

                        if (enc.species == 96 && enc.level == 15 && DeathFlyBot.godStats[enc.dvs]) {
                            DeathFlyBot.logLN("POTENTIAL GOD SHREW FOUND!");
                            DeathFlyBot.logF(
                                    "inputs %s step %d cost %d encounter: species %d lv%d DVs %04X rng %s encrng %s\n",
                                    peg.path, step + 1, totalEncCost, enc.species, enc.level, enc.dvs, enc.battleRNG,
                                    rngAtEnc);
                        }
                    }

                    // progress
                    gb.loadState(curState);
                    wrap.injectRBInput(oogDir);
                    // skip past OJP we just
                    // hit, and then reach
                    // next one
                    wrap.advanceWithJoypadToAddress(oogDir, RedBlueAddr.joypadOverworldAddr+1);
                    wrap.advanceWithJoypadToAddress(oogDir, RedBlueAddr.joypadOverworldAddr);
                    // state save for next
                    // loop
                    curState = gb.saveState();
                    // change out-of-grass
                    // walking direction?
                    if (mem.getY() == 6 && oogDir == UP) {
                        oogDir = DOWN;
                    } else if (mem.getY() == 9 && oogDir == DOWN) {
                        oogDir = UP;
                    }
                }
            } else if (pathCost < DeathFlyBot.startPositionsCosts.get(peg.rngState)) {
                // Don't retest, but do
                // reconsider encounters
                DeathFlyBot.startPositionsCosts.put(peg.rngState, pathCost);
                for (String stateEnc : DeathFlyBot.startPositionsEncs.get(peg.rngState)) {
                    String[] encBits = stateEnc.split("\\/");
                    int cost = Integer.parseInt(encBits[4]);
                    int step = Integer.parseInt(encBits[6]);
                    String encRep = encBits[0] + "/" + encBits[1] + "/" + encBits[2] + "/" + encBits[3];
                    int totalEncCost = pathCost + cost;
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
        } finally {
            synchronized (threadsRunning) {
                threadsRunning[useIdx] = false;
            }
        }
    }

}
