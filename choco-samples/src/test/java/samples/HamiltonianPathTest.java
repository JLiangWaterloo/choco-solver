/*
 * Copyright (c) 1999-2014, Ecole des Mines de Nantes
 * All rights reserved.
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the Ecole des Mines de Nantes nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE REGENTS AND CONTRIBUTORS ``AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE REGENTS AND CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package samples;

import gnu.trove.list.array.TIntArrayList;
import org.testng.Assert;
import org.testng.annotations.Test;
import samples.graph.input.GraphGenerator;
import solver.Solver;
import solver.constraints.ICF;
import solver.exception.ContradictionException;
import solver.search.loop.monitors.SearchMonitorFactory;
import solver.search.measure.IMeasures;
import solver.search.strategy.ISF;
import solver.search.strategy.assignments.DecisionOperator;
import solver.search.strategy.decision.Decision;
import solver.search.strategy.decision.fast.FastDecision;
import solver.search.strategy.strategy.AbstractStrategy;
import solver.variables.IntVar;
import solver.variables.VF;
import util.PoolManager;
import util.objects.setDataStructures.SetType;

/**
 * Find a Hamiltonian path in a sparse graph with incremental algorithm
 * test the correctness of fine event recorders
 *
 * @author Jean-Guillaume Fages
 */
public class HamiltonianPathTest {

	private final static long TIME_LIMIT = 3000;

	@Test(groups = "10m")
	public static void test() {
		int[] sizes = new int[]{20, 50};
		long s;
		int[] nbVoisins = new int[]{3, 5, 10};
		boolean[][] matrix;
		for (int n : sizes) {
			for (int nb : nbVoisins) {
				for (int ks = 0; ks < 50; ks++) {
					s = System.currentTimeMillis();
//					System.out.println("n:" + n + " nbVoisins:" + nb + " s:" + s);
					GraphGenerator gg = new GraphGenerator(n, s, GraphGenerator.InitialProperty.HamiltonianCircuit);
					matrix = transformMatrix(gg.neighborBasedGenerator(nb));
					testInt(matrix, s, true, false);
					testInt(matrix, s, false, false);
					testInt(matrix, s, true, true);
					testInt(matrix, s, false, true);
				}
			}
		}
	}

	private static void testInt(boolean[][] matrix, long seed, boolean rd, boolean enumerated) {
		Solver solver = new Solver();
		int n = matrix.length;
		// build model
		IntVar[] succ = new IntVar[n];
		int offset = -5;
		TIntArrayList l = new TIntArrayList();
		for (int i = 0; i < n-1; i++) {
			l.clear();
			for (int j = 0; j < n; j++) {
				if(matrix[i][j]){
					l.add(j+offset);
				}
			}
			if(l.isEmpty())throw new UnsupportedOperationException();
			if(enumerated){
				succ[i] = VF.enumerated("suc",l.toArray(),solver);
			}else{
				succ[i] = VF.bounded("suc",offset,n+offset,solver);
				solver.post(ICF.member(succ[i],l.toArray()));
			}
		}
		succ[n-1] = VF.fixed(n+offset,solver);
		solver.post(ICF.path(succ,VF.fixed(offset,solver),VF.fixed(n-1+offset,solver),offset));
		// configure solver
		if (rd) {
			if(enumerated){
				solver.set(ISF.random_value(succ,seed));
			}else{
				solver.set(ISF.random_bound(succ, seed));
			}
		} else {
			solver.set(new ConstructorIntHeur(succ,offset));
		}
		SearchMonitorFactory.limitTime(solver, TIME_LIMIT);
		solver.findSolution();
		IMeasures mes = solver.getMeasures();
		// the problem has at least one solution
		Assert.assertFalse(mes.getSolutionCount() == 0 && mes.getTimeCount() < TIME_LIMIT/1000);
	}

	private static boolean[][] transformMatrix(boolean[][] m) {
		int n = m.length + 1;
		boolean[][] matrix = new boolean[n][n];
		for (int i = 0; i < n - 1; i++) {
			for (int j = 1; j < n - 1; j++) {
				matrix[i][j] = m[i][j];
			}
			matrix[i][n - 1] = m[i][0];
		}
		return matrix;
	}

	private static class ConstructorIntHeur extends AbstractStrategy<IntVar> {
		int n, offset;
		PoolManager<FastDecision> pool;

		public ConstructorIntHeur(IntVar[] v, int off) {
			super(v);
			offset = off;
			n = v.length;
			pool = new PoolManager<>();
		}

		@Override
		public void init() throws ContradictionException {}

		@Override
		public Decision<IntVar> getDecision() {
			int x = 0;
			while (vars[x].isInstantiated()) {
				x = vars[x].getValue()-offset;
				if(x==vars.length){
					return null;
				}
			}
			FastDecision d = pool.getE();
			if(d==null)d=new FastDecision(pool);
			d.set(vars[x], vars[x].getLB(), DecisionOperator.int_eq);
			return d;
		}
	}
}
