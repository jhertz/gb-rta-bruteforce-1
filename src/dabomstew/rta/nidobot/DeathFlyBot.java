package dabomstew.rta.nidobot;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;

import mrwint.gbtasgen.Gb;
import dabomstew.rta.RedBlueAddr;
import dabomstew.rta.FileFunctions;
import dabomstew.rta.Func;
import dabomstew.rta.GBMemory;
import dabomstew.rta.GBWrapper;

public class DeathFlyBot {

    public static final int A = 0x01;
    public static final int B = 0x02;
    public static final int SELECT = 0x04;
    public static final int START = 0x08;

    public static final int RIGHT = 0x10;
    public static final int LEFT = 0x20;
    public static final int UP = 0x40;
    public static final int DOWN = 0x80;

    public static final int RESET = 0x800;

    public static DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

    public static boolean[] godStats;
    private static String runName;

    public static final int[] hopCosts = { 0, 131, 190, 298, 447 };
    public static final int gamefreakStallFrameCost = 254;
    public static Map<String, Integer> encountersCosts;
    public static Map<String, Integer> startPositionsCosts;
    public static Map<String, List<String>> startPositionsEncs;

    // Config

    public static final int maxAPresses = 1;
    public static final int minAPresses = 0;
    public static int minHops = 2;
    public static int maxHops = 4;
    public static final boolean doGamefreakStallIntro = false;
    public static final String gameName = "blue";
    public static final int maxCostAtStart = 999999;
    public static final int maxCostOfPath = 999999;
    public static final int maxStepsInGrassArea = 20;
    public static final int numEncounterThreads = 3;

    public static final byte MAP_ID = 0x33; //D35E
    public static final byte BASE_Y = 0x17; //D361
    public static final byte BASE_X = 0x04; //D362

    public static final int hops = 0;

    public static void main(String[] args) throws IOException, InterruptedException {

        String gameName = "blue";
        // Make folder if necessary
        if (!new File("logs").exists()) {
            new File("logs").mkdir();
        }

        if (!new File("roms").exists()) {
            new File("roms").mkdir();
            System.err.println("I need ROMs to simulate!");
            System.exit(0);
        }

        // Check byte buffer limits
        int maxBuffers = 0;
        int bbLimit = 0;
        List<ByteBuffer> allMyBuffers = new ArrayList<ByteBuffer>();
        try {
            while (true) {
                allMyBuffers.add(Gb.createDirectByteBuffer(190000));
                maxBuffers++;
            }
        } catch (OutOfMemoryError ex) {
            bbLimit = maxBuffers * 95 / 100;
            System.out.println("ran out of memory at " + maxBuffers + " byte buffers, set limit to " + bbLimit);
        }
        allMyBuffers.clear();
        allMyBuffers = null;
        System.gc();

        // Init gambatte with 1 screen
        Gb.loadGambatte(numEncounterThreads);

        // config

        runName = "nopalette_" + gameName + "_hop_" + hops;
        if (doGamefreakStallIntro) {
            runName = "nopalette_" + gameName + "_stall";
        }
        initLog();


        int[] firstLoopAddresses = { RedBlueAddr.joypadOverworldAddr };
        int[] subsLoopAddresses = { RedBlueAddr.joypadOverworldAddr, RedBlueAddr.printLetterDelayAddr };

        // setup starting positions
        StartingPositionManager spm = new StartingPositionManager();

        // first rect to include, use more if needed
        //top left: 0x17, 0x01
        //bottom left: 0x19, 0x01
        //top right: 0x17, 0x06
        //bottom right 0x19, 0x06

        spm.includeRect(0x17, 0x01, 0x19, 0x06); // main rectangle of save save positions

        // TBD: if these^ arent enough positions, there are also some other ones, but the paths are trickier to not load the item
        // if we can just use the above rect, that should be ezpz

        spm.exclude(0x18, 0x04); // sign

        encountersCosts = new ConcurrentHashMap<>();
        startPositionsCosts = new ConcurrentHashMap<>();
        startPositionsEncs = new ConcurrentHashMap<>();


        long starttime = System.currentTimeMillis() / 1000L;
        String logFilename = "logs/deathfly" + runName + "_" + starttime + ".log";
        if (new File(logFilename).exists()) {
            new File(logFilename).delete();
        }
        PrintStream ps = new PrintStream(logFilename, "UTF-8");
        //hops = 0;
        Position pos = spm.iterator();
        try {
            int baseCost = 0;
            ps.printf("Starting position: map %d x %d y %d cost %d\n", pos.map, pos.x, pos.y, baseCost);
            logF("testing starting position x=%d y=%d map=%d cost=%d\n", pos.x, pos.y, pos.map, baseCost);

            //time to make our base save
            makeSave("deathfly", MAP_ID, pos.x, pos.y);


            Gb[] gbs = new Gb[numEncounterThreads];
            GBMemory[] mems = new GBMemory[numEncounterThreads];
            GBWrapper[] wraps = new GBWrapper[numEncounterThreads];

            for (int i = 0; i < numEncounterThreads; i++) {
                gbs[i] = new Gb(i, false);
                gbs[i].startEmulator("roms/poke" + gameName + ".gbc");
                gbs[i].step(0); // let gambatte initialize itself
                mems[i] = new GBMemory(gbs[i]);
                wraps[i] = new GBWrapper(gbs[i], mems[i]);
            }

            Set<PositionEnteringGrass> endPositions = new HashSet<PositionEnteringGrass>();
            // we only have one endposition that works for a deathfly

            {
                Gb gb = gbs[0];
                GBMemory mem = mems[0];
                GBWrapper wrap = wraps[0];
                int introInputCtr = 0;
                if (doGamefreakStallIntro) {
                    wrap.advanceToAddress(RedBlueAddr.delayAtEndOfShootingStarAddr);
                    int[] introInputs = { B | SELECT | UP, START, A, A };
                    while (introInputCtr < 4) {
                        wrap.advanceToAddress(RedBlueAddr.joypadAddr);
                        // inject intro inputs
                        wrap.injectRBInput(introInputs[introInputCtr++]);
                        wrap.advanceFrame();
                    }
                } else {
                    int[] introInputs = { B | SELECT | UP, B | SELECT | UP, START, A, A };
                    if (hops > 0) {
                        introInputs = new int[] { A, START, A, START | A, START | A };
                    }
                    while (introInputCtr < 5) {
                        wrap.advanceToAddress(RedBlueAddr.joypadAddr);
                        // inject intro inputs
                        wrap.injectRBInput(introInputs[introInputCtr++]);
                        wrap.advanceFrame();
                    }
                }

                // overworld loop
                boolean checkingPaths = true;
                Set<String> seenStates = new HashSet<String>();
                Map<String, String> statePaths = new HashMap<String, String>();
                Stack<OverworldStateAction> actionQueue = new Stack<OverworldStateAction>();

                int numEndPositions = 0, numStatesChecked = 0;
                String lastPath = "";
                long start = System.currentTimeMillis();
                int[] addresses = firstLoopAddresses;
                int startX = -1, startY = -1;
                int lastInput = 0;
                while (checkingPaths) {
                    int result = wrap.advanceWithJoypadToAddress(lastInput,addresses);
                    String curState = mem.getUniqid();

                    boolean garbage = mem.getTurnFrameStatus() != 0 || result != RedBlueAddr.joypadOverworldAddr;
                    if(garbage) {
                        if(mem.getTurnFrameStatus() != 0) {
                            System.out.println("discarded for TFS != 0");
                            if(mem.isDroppingInputs()) {
                                System.out.println("Uhhhhh");
                            }
                        }
                        else {
                            System.out.println("discarded for not joypadoverworld");
                        }
                    }
                    if (!garbage && lastInput != 0 && lastInput != A) {
                        if (mem.getX() == startX && mem.getY() == startY) {
                            garbage = true;
                            System.out.println("discarded for not moving");
                        }
                    }
                    if (!garbage) {
                        int actions = PermissibleActionsHandler.actionsGoingToGrass(mem.getMap(), mem.getX(),
                                mem.getY());
                        int inputsNextA = Func.inputsUntilNextA(lastPath, maxAPresses);
                        if (!seenStates.contains(curState)) {
                            seenStates.add(curState);
                            statePaths.put(curState, lastPath);
                            ByteBuffer curSave = gb.saveState();
                            //System.out.println("map="+mem.getMap()+" x ="+mem.getX()+" y="+mem.getY()+" inputs="+actions);
                            if (actions != 0) {
                                for (int inp = 0x10; inp < 0x100; inp *= 2) {
                                    if ((actions & inp) != 0) {
                                        OverworldStateAction action = new OverworldStateAction(curState,
                                                curSave, inp);
                                        actionQueue.add(action);
                                    }
                                }
                                if (inputsNextA == 0) {
                                    OverworldStateAction action = new OverworldStateAction(curState, curSave, A);
                                    actionQueue.add(action);
                                }
                            } else {
                                if (minAPresses == 0 || Func.aCount(lastPath, lastPath.length()) >= minAPresses) {
                                    // TODO: this needs to change, since theres only one goal position
                                    endPositions.add(new PositionEnteringGrass(curSave, lastPath, mem
                                            .getRNGState()));
                                    numEndPositions++;
                                    if (numEndPositions >= bbLimit) {
                                        long end = System.currentTimeMillis();
                                        logLN(".done part 1; cutoff early after " + numStatesChecked
                                                + " states and found " + numEndPositions + " results in "
                                                + (end - start) + "ms");
                                        checkingPaths = false;
                                    }
                                }
                            }
                        } else if (inputsNextA < Func.inputsUntilNextA(statePaths.get(curState), maxAPresses)) {
                            statePaths.put(curState, lastPath);
                            if (inputsNextA == 0 && actions != 0) {
                                ByteBuffer curSave = gb.saveState();
                                OverworldStateAction action = new OverworldStateAction(curState, curSave, A);
                                actionQueue.push(action);
                            }
                        } else {
                            //System.out.println("discarded for dupe: "+lastPath+"/"+curState);
                        }
                    }

                    // Next position
                    if (!actionQueue.isEmpty() && checkingPaths) {
                        numStatesChecked++;
                        addresses = subsLoopAddresses;
                        OverworldStateAction actionToTake = actionQueue.pop();
                        String inputRep = Func.inputName(actionToTake.nextInput);
                        gb.loadState(actionToTake.savedState);
                        wrap.injectRBInput(actionToTake.nextInput);
                        lastInput = actionToTake.nextInput;
                        startX = mem.getX();
                        startY = mem.getY();
                        lastPath = statePaths.get(actionToTake.statePos) + inputRep;
                        // skip the joypadoverworld we just hit
                        wrap.advanceToAddress(RedBlueAddr.joypadOverworldAddr+1);
                    } else if (checkingPaths) {
                        long end = System.currentTimeMillis();
                        logLN(".done part 1; checked " + numStatesChecked + " states and found "
                                + numEndPositions + " results in " + (end - start) + "ms");
                        checkingPaths = false;
                    }
                }
                ps.flush();
            }

            // Encounter bruteforcing

            long lastOffset = System.currentTimeMillis();
            {
                Iterator<PositionEnteringGrass> grassEncs = endPositions.iterator(); // !!!
                boolean[] threadsRunning = new boolean[numEncounterThreads];
                while (grassEncs.hasNext()) {

                    synchronized (threadsRunning) {
                        int useNum = -1;
                        for (int i = 0; i < numEncounterThreads; i++) {
                            if (!threadsRunning[i]) {
                                useNum = i;
                                break;
                            }
                        }

                        if (useNum != -1) {
                            threadsRunning[useNum] = true;
                            // Start a new Thread
                            new EncounterCheckThreadDeathFly(grassEncs.next(), gbs[useNum], mems[useNum],
                                    wraps[useNum], baseCost, ps, threadsRunning, useNum).start();
                        }
                    }
                    Thread.sleep(1);
                }

                boolean allDone = false;
                while (!allDone) {
                    synchronized (threadsRunning) {
                        allDone = true;
                        for (int i = 0; i < numEncounterThreads; i++) {
                            if (threadsRunning[i]) {
                                allDone = false;
                            }
                        }
                    }
                    Thread.sleep(5);
                }
            }

            logLN(".done part 2 in " + (System.currentTimeMillis() - lastOffset) + "ms");
        } catch (OutOfMemoryError ex) {
            logLN("failed due to memory fail");
        }
        System.gc();
        ps.flush();
        ps.close();

        long currtime = System.currentTimeMillis() / 1000L;

        PrintStream statesLog = new PrintStream("logs/shrew_statestested_" + runName + "_" + currtime + ".log", "UTF-8");
        for (String state : startPositionsCosts.keySet()) {
            statesLog.println(state);
            statesLog.println(startPositionsCosts.get(state));
            for (String stateEnc : startPositionsEncs.get(state)) {
                statesLog.println(stateEnc);
            }
            statesLog.println("EOSTATE");
        }
        statesLog.flush();
        statesLog.close();
        logLN("dumped rng states to shrew_statestested_" + runName + "_" + currtime + ".log");
        Files.copy(new File("logs/shrew_statestested_" + runName + "_" + currtime + ".log").toPath(), new File(gameName
                + "_statesimport_shrew.txt").toPath(), StandardCopyOption.REPLACE_EXISTING);

        PrintStream encsLog = new PrintStream("logs/shrew_encsfound_" + runName + "_" + currtime + ".log", "UTF-8");
        for (String enc : encountersCosts.keySet()) {
            encsLog.println(enc);
            encsLog.println(encountersCosts.get(enc));
        }
        encsLog.flush();
        encsLog.close();
        logLN("dumped encounters to shrew_encsfound_" + runName + "_" + currtime + ".log");
        Files.copy(new File("logs/shrew_encsfound_" + runName + "_" + currtime + ".log").toPath(), new File(gameName
                + "_encounters_shrew.txt").toPath(), StandardCopyOption.REPLACE_EXISTING);

        closeLog();
    }

    public static void makeSave(String baseName, int map, int x, int y) throws IOException {
        byte[] baseSave = FileFunctions.readFileFullyIntoBuffer("baseSaves/" + baseName + ".sav");
        int mapWidth = 17; //viridian forrest width
        int baseX = x;
        int baseY = y;
        int tlPointer = 0xC6E8 + (baseY / 2 + 1) * (mapWidth + 6) + (baseX / 2 + 1);
        baseSave[0x260B] = (byte) (tlPointer & 0xFF);
        baseSave[0x260C] = (byte) (tlPointer >> 8);
        baseSave[0x260D] = (byte) baseY;
        baseSave[0x260E] = (byte) baseX;
        baseSave[0x260F] = (byte) (baseY % 2);
        baseSave[0x2610] = (byte) (baseX % 2);
        baseSave[0x2CEF] = (byte) 40;
        baseSave[0x2CF0] = 0;
        baseSave[0x2CF1] = 0;
        int csum = 0;
        for (int i = 0x2598; i < 0x3523; i++) {
            csum += baseSave[i] & 0xFF;
        }
        baseSave[0x3523] = (byte) ((csum & 0xFF) ^ 0xFF); // cpl
        FileFunctions.writeBytesToFile("roms/poke" + gameName + ".sav", baseSave);
    }

    private static PrintStream mainLogCopy;

    public static void initLog() throws FileNotFoundException, UnsupportedEncodingException {
        long currtime = System.currentTimeMillis() / 1000L;
        mainLogCopy = new PrintStream("logs/shrew_consolelog_" + runName + "_" + currtime + ".log", "UTF-8");
    }

    public static void closeLog() {
        mainLogCopy.flush();
        mainLogCopy.close();
    }

    public static void logF(String format, Object... args) {
        logDate();
        System.out.printf(format, args);
        mainLogCopy.printf(format, args);
        mainLogCopy.flush();
    }

    public static void logLN(String ln) {
        logDate();
        System.out.println(ln);
        mainLogCopy.println(ln);
        mainLogCopy.flush();
    }

    private static void logDate() {
        Date date = new Date();
        mainLogCopy.print("[" + dateFormat.format(date) + "] ");
    }

}
