/*
 * Copyright 2022 Aleksei Balan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ab;

import java.util.Random;
import java.util.function.UnaryOperator;

/**
 * Exponential function. y=ae^bx+c
 */
public class ExponentialFunction implements UnaryOperator<Double> {
  private final double aConstant;
  private final double bConstant;
  private final double cConstant;

  public ExponentialFunction(double x1, double x3, double y1, double y2, double y3) {
    double d = (x3 - x1) / 2;
    double r = (y3 - y2) / (y2 - y1);
    bConstant = Math.log(r) / d;
    aConstant = (y3 - y1) / (Math.pow(r, x3 / d) - Math.pow(r, x1 / d));
    cConstant = y2 - aConstant * Math.exp(bConstant * (x3 + x1) / 2);
    if (Double.isInfinite(aConstant) || Double.isInfinite(cConstant)) throw new ArithmeticException();
  }

  @Override
  public Double apply(Double d) {
    return aConstant * Math.exp(bConstant * d) + cConstant;
  }

  public int apply(int i) {
    return (int) Math.round(this.apply(Double.valueOf(i)));
  }

  public static class RandomInt {
    ExponentialFunction exponentialFunction;
    Random random;
    public RandomInt(Random random, double y1, double y2, double y3) {
      this.exponentialFunction = new ExponentialFunction(Integer.MIN_VALUE, Integer.MAX_VALUE, y1, y2, y3);
      this.random = random;
    }
    public int nextInt() {
      return (int) Math.round(exponentialFunction.apply((double) random.nextInt()));
    }
  }
}
