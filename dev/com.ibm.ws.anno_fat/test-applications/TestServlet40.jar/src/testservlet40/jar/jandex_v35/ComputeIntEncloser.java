package testservlet40.jar.jandex_v35;

public class ComputeIntEncloser {

	public static interface ComputeInt {
		public int compute();
	}

	private static final int x;

	static {
		ComputeInt computeInt = new ComputeInt() {
			@Override
			public int compute() { return 0; }
		};
		
		x = computeInt.compute();
	}

}
