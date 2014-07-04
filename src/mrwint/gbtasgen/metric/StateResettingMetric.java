package mrwint.gbtasgen.metric;

import mrwint.gbtasgen.state.State;

public abstract class StateResettingMetric extends Metric {

	@Override
	public int getMetric() {
		State s = new State();
		int ret = getMetricInternal();
		s.restore();
		return ret;
	}
	public abstract int getMetricInternal();
}