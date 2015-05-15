package mrwint.gbtasgen;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Gb {
	public Gb(){}

	public static void loadGambatte(){
		System.loadLibrary("gambatte");
	}

	public static native void startEmulator(String rom);

	public static native void nstep(int keymask);
	public static native int nstepUntil(int keymask, int[] addresses);
	public static native void nreset();

	public static int step(int keymask, int... addresses){
		if ((keymask & 0x800) > 0)
			nreset();
		if(addresses.length == 0) {
			nstep(keymask);
			return 0;
		}
		return nstepUntil(keymask,addresses);
	}

	public static native long saveState(ByteBuffer buffer, int size);
	public static native void loadState(ByteBuffer buffer, int size);

//  public static final int MAX_SAVE_SIZE = 211243;
//  public static final int MAX_SAVE_SIZE = 153894; // Tetris
//  public static final int MAX_SAVE_SIZE = 162092; // Sml2_10
  public static final int MAX_SAVE_SIZE = 186666; // PokeRed

	public static ByteBuffer createDirectByteBuffer(int capacity){
		byte[] zeros = new byte[capacity];
		ByteBuffer buf =
			ByteBuffer.allocateDirect(capacity)
			          .order(ByteOrder.nativeOrder());
		buf.put(zeros);
		buf.clear();
		return buf;
	}

	public static ByteBuffer saveBuffer = createDirectByteBuffer(MAX_SAVE_SIZE);

	public static ByteBuffer saveState(){
		ByteBuffer buf = createDirectByteBuffer(MAX_SAVE_SIZE);
		saveState(buf, buf.capacity());
		buf.rewind();
		return buf;
	}

	public static void loadState(ByteBuffer saveState){
		loadState(saveState, saveState.capacity());
	}

	public static native int getROMSize();
	public static final int NUM_REGISTERS = 6;
	public static final int GB_MEMORY = 0x10000;

	public static native void getROM(int[] store);
	public static native void getRegisters(int[] store);
	public static native void getMemory(int[] store);
	public static native int readMemory(int address);
	public static native void writeMemory(int address, int value);

	public static native int getRNGState();
	public static native void offsetRNG(int offset);
}
